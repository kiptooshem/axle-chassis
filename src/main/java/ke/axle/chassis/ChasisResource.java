/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ke.axle.chassis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import io.swagger.annotations.*;
import ke.axle.chassis.annotations.*;
import ke.axle.chassis.exceptions.ExpectationFailed;
import ke.axle.chassis.exceptions.GeneralBadRequest;
import ke.axle.chassis.utils.*;
import ke.axle.chassis.wrappers.ActionWrapper;
import ke.axle.chassis.wrappers.ResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.persistence.*;
import javax.persistence.criteria.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exposes the following resource actions;
 * <ul>
 * <li>Create new resource</li>
 * <li>Update existing resource</li>
 * <li>Delete existing resource</li>
 * <li>Approve checker actions</li>
 * <li>Decline checker actions</li>
 * <li>Fetch, filter, search and paginate resources</li>
 * </ul>
 *
 * param <T> action entity
 * param <E> id class
 * param <R> Edited entity
 * @author Cornelius M
 * @version 0.0.1
 * @author Owori Juma
 * @version 1.2.3
 */
public class ChasisResource<T, E extends Serializable, R> {

    /**
     * Used to handling logging requests
     */
    protected LoggerService loggerService;

    /**
     * Used to handle repository transactions
     */
    protected EntityManager entityManager;

    /**
     * Used to handle update requests
     */
    protected SupportRepository<T, E> supportRepo;

    /**
     *
     */
    protected Logger log = LoggerFactory.getLogger(this.getClass());
    private final List<Class> genericClasses;
    private String fieldNickname = "field";
    private final String recordName;


    public ChasisResource(LoggerService loggerService, EntityManager entityManager) {
        this.loggerService = loggerService;
        this.entityManager = entityManager;
        this.genericClasses = SharedMethods.getGenericClasses(this.getClass());
        this.supportRepo = new SupportRepository<>(entityManager, this.genericClasses.get(0), this.genericClasses.get(2));
        NickName nickName = AnnotationUtils.findAnnotation(this.genericClasses.get(0), NickName.class);
        this.recordName = (nickName == null) ? "Record" : nickName.name();
        log.debug("Assigned record name {} to entity {}", this.recordName, this.genericClasses.get(0));
    }

    /**
     * Used to persist new entities to the database. The following validations
     * are carried out before an entity is persisted:
     * If an id field is present on the entity it will be reset to null.
     * <ul>
     * <li>201 on success</li>
     * <li>409 on unique validation error</li>
     * <li>400 on validation error</li>
     * <li>404 for @{link ManyToOne} entities that don't exist</li>
     * </ul>
     */
    @RequestMapping(method = RequestMethod.POST)
    @Transactional
    @ApiOperation(value = "Create New Record")
    public ResponseEntity<ResponseWrapper<T>> create(@Valid @RequestBody T t) {
        ResponseWrapper response = new ResponseWrapper();
        //check if relational entities exists
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(t);
        for (Field field : t.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                accessor.setPropertyValue(field.getName(), null);
            }
            if (field.isAnnotationPresent(ManyToOne.class)) {
                Object relEntity = accessor.getPropertyValue(field.getName());
                if (relEntity != null) {
                    for (Field f2 : relEntity.getClass().getDeclaredFields()) {
                        if (f2.isAnnotationPresent(Id.class)) {
                            
                            Object id = accessor.getPropertyValue(f2.getName());
                            if (entityManager.find(relEntity.getClass(), id) == null) {
                                NickName nickName = relEntity.getClass().getDeclaredAnnotation(NickName.class);
                                if (nickName != null) {
                                    this.loggerService.log(nickName.name() + " with id " + id + " doesn't exist",
                                            t.getClass().getSimpleName(), null, AppConstants.ACTIVITY_CREATE, AppConstants.STATUS_FAILED, "");
                                    response.setMessage(nickName.name() + " with id " + id + " doesn't exist");
                                } else {
                                    this.loggerService.log("Record with id " + id + " doesn't exist",
                                            t.getClass().getSimpleName(), null, AppConstants.ACTIVITY_CREATE, AppConstants.STATUS_FAILED, "");
                                    response.setMessage("Record with id " + id + " doesn't exist");
                                }
                                response.setCode(404);
                                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                            }
                        }
                    }
                }
            }
        }
        //validate unique fields
        try {
            this.validateUniqueFields(t);
        } catch (GeneralBadRequest ex) {
            loggerService.log("Updating " + recordName + " failed due to record with similar " + fieldNickname + " exists", t.getClass().getSimpleName(),
                    null, AppConstants.ACTIVITY_CREATE, AppConstants.STATUS_FAILED, "");
            response.setCode(HttpStatus.CONFLICT.value());
            response.setMessage(ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        
        try {
            accessor.setPropertyValue("actionStatus", AppConstants.STATUS_UNAPPROVED);
        } catch (org.springframework.beans.NotWritablePropertyException ex) {
            log.debug("Field actionStatus on entity {} is not accessible skipping field", this.genericClasses.get(0));
        }
        try {
            accessor.setPropertyValue("action", AppConstants.ACTIVITY_CREATE);
        } catch (org.springframework.beans.NotWritablePropertyException ex) {
            log.debug("Field action on entity {} is not accessible skipping field", this.genericClasses.get(0));
        }
        try {
            accessor.setPropertyValue("intrash", AppConstants.NO);
        } catch (org.springframework.beans.NotWritablePropertyException ex) {
            log.debug("Field action on entity {} is not accessible skipping field", this.genericClasses.get(0));
        }
        entityManager.persist(t);
        this.loggerService.log("Created " + recordName + " successfully",
                t.getClass().getSimpleName(), SharedMethods.getEntityIdValue(t),
                AppConstants.ACTIVITY_CREATE, AppConstants.STATUS_COMPLETED, "");
        response.setData(t);
        response.setCode(201);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Used to fetch entity by id
     *
     * param id Entity id
     * return {link ResponseEntity} with data field containing the entity
     * (data is null when entity could not be found) and status 200:
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch single record using record id")
    public ResponseEntity<ResponseWrapper<T>> getEntity(@PathVariable("id") E id) {
        ResponseWrapper response = new ResponseWrapper();
        response.setData(this.fetchEntity(id));
        return ResponseEntity.ok(response);
    }

    /**
     * Used to update entities by saving new changes to the edited record
     * entity. For edited record to work The following annotation must be
     * present on the relevant fields to be used to store changes;
     * <ul>
     * <li> @{link EditEntity} used to store the name of the entity being
     * updated preferably should be a string For example
     * <p>
     * <code>@{link EditEntity} <br> private {link String}
     * recordEntity;</code></p>
     * </li>
     * <li>@{link EditDataWrapper} used to store changes in JSON format
     * preferably should be a {link String} For example
     * <p>
     * <code>@{link EditDataWrapper}<br>private {link String} data;</code></p>
     * </li>
     * <li>@{link EditEntityId} used to store entity id and you can use any
     * data type that extends {link Serializable} For example
     * <p>
     * <code>@{link EditEntityId}<br> private {link Long} entityId; </code>
     * </p>
     * </li>
     * </ul>
     *
     * <h4>Note</h4>
     * For created and updated records that have not been approved the changes
     * are persisted to the entity directly without being stored in the edited
     * record entity
     *
     * param t entity containing new changes
     * return {link ResponseEntity} with statuses:
     * <ul>
     * <li>200 on success</li>
     * <li>404 if the entity doesn't exist in the database</li>
     * <li>400 on validation errors (Relies on javax validation)</li>
     * <li>409 on unique field validation errors</li>
     * <li>417 if the entity us pending approval actions or if changes were not
     * found on the current entity</li>
     * </ul>
     * throws IllegalAccessException if a field on the entity could not be
     * accessed
     * throws JsonProcessingException if changes could not be converted to json
     * string
     * throws
     * ExpectationFailed When
     * editEntity does not have fields; @{link EditEntity}, @{link EditEntity}
     * and @{link EditEntityId}
     */
    @RequestMapping(method = RequestMethod.PUT)
    @ApiOperation(value = "Update record")
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "Record not found")
        ,
            @ApiResponse(code = 417, message = "Record has unapproved actions or if record has not been modified")
    })
    @Transactional
    public ResponseEntity<ResponseWrapper<T>> updateEntity(@RequestBody @Valid T t) throws IllegalAccessException, JsonProcessingException, ExpectationFailed {
        ResponseWrapper<T> response = new ResponseWrapper();
        
        T dbT = this.fetchEntity((Serializable) SharedMethods.getEntityIdValue(t));
        if (dbT == null) {
            loggerService.log("Updating " + recordName + " failed due to record doesn't exist", t.getClass().getSimpleName(),
                    null, AppConstants.ACTIVITY_CREATE, AppConstants.STATUS_FAILED, "");
            response.setCode(HttpStatus.NOT_FOUND.value());
            response.setMessage("Sorry failed to locate record with the specified id");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        try {
            this.validateUniqueFields(t);
        } catch (GeneralBadRequest ex) {
            loggerService.log("Updating " + recordName + " failed due to record with similar " + fieldNickname + " exists", t.getClass().getSimpleName(),
                    null, AppConstants.ACTIVITY_UPDATE, AppConstants.STATUS_FAILED, "");
            response.setCode(HttpStatus.CONFLICT.value());
            response.setMessage(ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(dbT);
        String actionStatus = null;
        String action = null;
        try {
            actionStatus = (String) accessor.getPropertyValue("actionStatus");
        } catch (org.springframework.beans.NotWritablePropertyException ex) {
            log.debug("Field actionStatus on entity {} is not accessible skipping field", this.genericClasses.get(0));
        }
        try {
            action = (String) accessor.getPropertyValue("action");
        } catch (org.springframework.beans.NotWritablePropertyException ex) {
            log.debug("Field action on entity {} is not accessible skipping field", this.genericClasses.get(0));
        }
        if (action != null && actionStatus != null) {
            if ((AppConstants.STATUS_UNAPPROVED).equalsIgnoreCase(actionStatus) && ((action.equalsIgnoreCase(AppConstants.ACTIVITY_CREATE) || action.equalsIgnoreCase(AppConstants.ACTIVITY_UPDATE)))) {
                loggerService.log("Updating " + recordName + " failed due to record has unapproved actions",
                        t.getClass().getSimpleName(), SharedMethods.getEntityIdValue(t),
                        AppConstants.ACTIVITY_CREATE, AppConstants.STATUS_FAILED, "");
                response.setCode(HttpStatus.EXPECTATION_FAILED.value());
                response.setMessage("Sorry record has Unapproved actions");
                return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(response);
            }
            
            if (!(AppConstants.STATUS_UNAPPROVED).equalsIgnoreCase(actionStatus)) {
                accessor.setPropertyValue("action", AppConstants.ACTIVITY_UPDATE);
                accessor.setPropertyValue("actionStatus", AppConstants.STATUS_UNAPPROVED);
            }
        }
        
        List<String> changes;
        if (AppConstants.ACTIVITY_CREATE.equalsIgnoreCase(action) && AppConstants.STATUS_UNAPPROVED.equalsIgnoreCase(actionStatus)) {
            changes = supportRepo.fetchChanges(t, dbT);
            t = supportRepo.updateEdit(t, dbT);
        } else {
            this.entityManager.merge(dbT);
            if (this.genericClasses.get(0).isAnnotationPresent(EditChildEntity.class)) {
                changes = supportRepo.handleEditRequestChild(t, dbT, this.genericClasses.get(2));
            } else {
                changes = supportRepo.handleEditRequest(t, dbT, this.genericClasses.get(2));
                if (changes.isEmpty()) {
                    response.setCode(HttpStatus.EXPECTATION_FAILED.value());
                    response.setMessage("Sorry record has not been modified");
                    return new ResponseEntity(response, HttpStatus.EXPECTATION_FAILED);
                }
            }
        }
        
        response.setData(t);
        loggerService.log("Updated " + recordName + " successfully. "
                + String.join(",", changes),
                t.getClass().getSimpleName(), SharedMethods.getEntityIdValue(t),
                AppConstants.ACTIVITY_UPDATE, AppConstants.STATUS_COMPLETED, "");
        
        return ResponseEntity.ok(response);
        
    }

    /**
     * Used to delete entities.
     * <h4>Note</h4>
     * If action and actionStatus fields don't exist the record is moved to
     * trash directly (flagging .
     * <b>If intrash field doesn't exist the record is deleted permanently</b>
     *
     * param actions contains an array of entity id(s)
     * return {link ResponseEntity} with statuses:
     * <ul>
     * <li>200 on success</li>
     * <li>207 if not all records could be deleted</li>
     * <li>404 if the entity doesn't exist in the database</li>
     * </ul>
     */
    @RequestMapping(method = RequestMethod.DELETE)
    @ApiOperation(value = "Delete record")
    @ApiResponses(value = {
        @ApiResponse(code = 207, message = "Some records could not be processed successfully")
    })
    @Transactional
    public ResponseEntity<ResponseWrapper> deleteEntity(@RequestBody @Valid ActionWrapper<E> actions) {
        ResponseWrapper response = new ResponseWrapper();
        List<String> errors = new ErrorList();
        for (E id : actions.getIds()) {
            T t = supportRepo.fetchEntity(id);
            if (t == null) {
                loggerService.log("Deleting " + recordName + " failed due to record doesn't exist", this.genericClasses.get(0).getSimpleName(),
                        null, AppConstants.ACTIVITY_DELETE, AppConstants.STATUS_FAILED, "");
                errors.add(recordName + " with id " + id + " doesn't exist");
                continue;
            }
            
            PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(t);
            try {
                if ((accessor.getPropertyValue("actionStatus") != null) && accessor.getPropertyValue("actionStatus").toString().equalsIgnoreCase(AppConstants.STATUS_UNAPPROVED)) {
                    loggerService.log("Failed to delete " + recordName + ". Record has unapproved actions",
                            t.getClass().getSimpleName(), id, AppConstants.ACTIVITY_DELETE, AppConstants.STATUS_FAILED, "");
                    errors.add("Record has unapproved actions");
                } else {
                    accessor.setPropertyValue("action", AppConstants.ACTIVITY_DELETE);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_UNAPPROVED);
//                    this.entityManager.persist(t);
                    loggerService.log("Deleted " + recordName + " successfully", this.genericClasses.get(0).getSimpleName(),
                            id, AppConstants.ACTIVITY_DELETE, AppConstants.STATUS_COMPLETED, "");
                }
            } catch (org.springframework.beans.NotWritablePropertyException e) {
                log.debug("Failed to find action and action status failed skipping "
                        + "updating action status proceeding to flag intrash field");
                try {
                    accessor.setPropertyValue("intrash", AppConstants.YES);
                } catch (org.springframework.beans.NotWritablePropertyException ex) {
                    log.warn("Failed to locate intrash field deleting the object permanently");
                    this.entityManager.remove(t);
                    this.entityManager.flush();
                }
            }
        }
        
        if (errors.isEmpty()) {
            return ResponseEntity.ok(response);
        } else {
            response.setCode(HttpStatus.MULTI_STATUS.value());
            response.setData(errors);
            response.setMessage(AppConstants.CHECKER_GENERAL_ERROR);
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
        }
        
    }
    
    @ApiOperation(value = "Approve Record Actions")
    @ApiResponses(value = {
        @ApiResponse(code = 207, message = "Some records could not be processed successfully")
    })
    /**
     * Used to approve actions (create, update, delete, deactivate). Also
     * ensures only the checker can approve an action
     *
     * param actions containing entities id
     * return {link ResponseEntity} with statuses:
     * <ul>
     * <li>200 on success</li>
     * <li>404 if the entity doesn't exist in the database</li>
     * <li>207 if some of the action could be approved successfuly. The data
     * fields contains more details on records that failed</li>
     * </ul>
     * throws
     * ExpectationFailed When
     * entity doesn't have action or actionStatus fields
     */
    @RequestMapping(value = "/approve-actions", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<ResponseWrapper> approveActions(@RequestBody @Valid ActionWrapper<E> actions) throws ExpectationFailed {
        ResponseWrapper response = new ResponseWrapper();
        List<String> errors = new ErrorList();
        
        for (E id : actions.getIds()) {
            T t = this.fetchEntity(id);
            try {
                if (t == null) {
                    loggerService.log("Failed to approve " + recordName + ". Failed to locate record with specified id",
                            this.genericClasses.get(0).getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    errors.add(recordName + " with id " + id + " doesn't exist");
                    continue;
                }
                
                PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(t);
                String action;
                String actionStatus;
                try {
                    if (accessor.getPropertyValue("action") == null) {
                        log.warn("action is null on entity {} assigning empty string", t);
                        action = "";
                    } else {
                        action = accessor.getPropertyValue("action").toString();
                    }
                    if (accessor.getPropertyValue("actionStatus") == null) {
                        log.warn("actionStatus is null on entity {} assigning empty string", t);
                        actionStatus = "";
                    } else {
                        actionStatus = accessor.getPropertyValue("actionStatus").toString();
                    }
                } catch (org.springframework.beans.NotWritablePropertyException ex) {
                    throw new ExpectationFailed("Sorry entity does not contain action and actionStatus fields");
                }
                
                if (loggerService.isInitiator(this.genericClasses.get(0).getSimpleName(), id, action) && !action.equalsIgnoreCase("Unconfirmed")) {
                    errors.add("Sorry failed to approve " + recordName + ". Maker can't approve their own record ");
                    loggerService.log("Failed to approve " + recordName + ". Maker can't approve their own record",
                            SharedMethods.getEntityName(this.genericClasses.get(0)), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    continue;
                } else if (action.equalsIgnoreCase(AppConstants.ACTIVITY_CREATE)
                        && actionStatus.equalsIgnoreCase(AppConstants.STATUS_UNAPPROVED)) {//process new record
                    this.processApproveNew(id, t, actions.getNotes(), recordName);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_APPROVED);
                } else if (action.equalsIgnoreCase(AppConstants.ACTIVITY_UPDATE)
                        && actionStatus.equalsIgnoreCase(AppConstants.STATUS_UNAPPROVED)) {//process updated record
                    this.processApproveChanges(id, t, actions.getNotes(), recordName);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_APPROVED);
                } else if (action.equalsIgnoreCase(AppConstants.ACTIVITY_DELETE)
                        && actionStatus.equalsIgnoreCase(AppConstants.STATUS_UNAPPROVED)) {
                    this.processApproveDeletion(id, t, actions.getNotes(), recordName);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_APPROVED);
                } else if (action.equalsIgnoreCase(AppConstants.ACTIVITY_CREATE)
                        && actionStatus.equalsIgnoreCase(AppConstants.STATUS_UNCONFIRMED)) {
                    this.processConfirm(id, t, actions.getNotes(), recordName);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_UNAPPROVED);
                } else {
                    loggerService.log("Failed to approve " + recordName + ". Record doesn't have approve actions",
                            this.genericClasses.get(0).getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    errors.add(recordName + " with id " + id + " doesn't have approve actions");
                }
                this.entityManager.merge(t);
            } catch (ExpectationFailed ex) {
                errors.add(ex.getMessage());
            }
        }
        if (errors.isEmpty()) {
            return ResponseEntity.ok(response);
        } else {
            response.setCode(HttpStatus.MULTI_STATUS.value());
            response.setData(errors);
            response.setMessage(AppConstants.CHECKER_GENERAL_ERROR);
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
        }
    }

    
    protected void processApproveNew(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        loggerService.log("Done approving new  " + nickName + "",
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    
    protected void processApproveChanges(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        try {
            entity = supportRepo.mergeChanges(id, entity);
        } catch (IOException | IllegalArgumentException | IllegalAccessException ex) {
            log.error(AppConstants.AUDIT_LOG, "Failed to approve record changes", ex);
            throw new ExpectationFailed("Failed to approve record changes please contact the administrator for more help");
        }
        loggerService.log("Done approving " + nickName + " changes",
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    
    protected void processApproveDeletion(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        accessor.setPropertyValue("intrash", AppConstants.YES);
        loggerService.log("Done approving " + nickName + " deletion.",
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    
    protected void processConfirm(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        loggerService.log("Done confirmation " + nickName + ".",
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * Used to decline actions (create, update, delete, deactivate). Ensures
     * only the checker can decline an action
     *
     * param actions
     * return with statuses:
     * <ul>
     * <li>200 on success</li>
     * <li>404 if the entity doesn't exist in the database</li>
     * <li>207 if some of the action could be approved successfuly. The data
     * fields contains more details on records that failed</li>
     * </ul>
     * throws
     * ExpectationFailed When
     * entity doesn't have action or actionStatus fields
     */
    @RequestMapping(value = "/decline-actions", method = RequestMethod.PUT)
    @Transactional
    @ApiOperation(value = "Decline Record Actions")
    @ApiResponses(value = {
        @ApiResponse(code = 207, message = "Some records could not be processed successfully")
    })
    public ResponseEntity<ResponseWrapper> declineActions(@RequestBody @Valid ActionWrapper<E> actions) {
        ResponseWrapper response = new ResponseWrapper();
        
        Class clazz = SharedMethods.getGenericClasses(this.getClass()).get(0);
        List<String> errors = new ErrorList();
        
        for (E id : actions.getIds()) {
            T t = supportRepo.fetchEntity(id);
            try {
                if (t == null) {
                    loggerService.log("Failed to decline " + recordName + ". Failed to locate record with specified id",
                            clazz.getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    errors.add(recordName + " with id " + id + " doesn't exist");
                    continue;
                }
                
                PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(t);
                String action;
                String actionStatus;
                try {
                    if (accessor.getPropertyValue("action") == null) {
                        log.warn("action is null on entity {} assigning empty string", t);
                        action = "";
                    } else {
                        action = accessor.getPropertyValue("action").toString();
                    }
                    if (accessor.getPropertyValue("actionStatus") == null) {
                        log.warn("actionStatus is null on entity {} assigning empty string", t);
                        actionStatus = "";
                    } else {
                        actionStatus = accessor.getPropertyValue("actionStatus").toString();
                    }
                } catch (org.springframework.beans.NotWritablePropertyException ex) {
                    throw new ExpectationFailed("Sorry entity does not contain action and actionStatus fields");
                }
                
                if (loggerService.isInitiator(clazz.getSimpleName(), id, action) && !action.equalsIgnoreCase("Unconfirmed")) {
                    errors.add("Sorry maker can't approve their own record ");
                    loggerService.log("Failed to approve " + recordName + ". Maker can't approve their own record",
                            SharedMethods.getEntityName(clazz), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    continue;
                } else if (action.equalsIgnoreCase(AppConstants.ACTIVITY_CREATE)
                        && actionStatus.equalsIgnoreCase(AppConstants.STATUS_UNAPPROVED)) {//process new record
                    this.processDeclineNew(id, t, actions.getNotes(), recordName);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_DECLINED);
                    try {
                        accessor.setPropertyValue("intrash", AppConstants.YES);
                    } catch (org.springframework.beans.NotWritablePropertyException ex) {
                        log.warn("Failed to locate intrash field deleting the object permanently");
                        this.entityManager.remove(t);
                        this.entityManager.flush();
                    }
                } else if (action.equalsIgnoreCase(AppConstants.ACTIVITY_UPDATE)
                        && actionStatus.equalsIgnoreCase(AppConstants.STATUS_UNAPPROVED)) {//process updated record
                    this.processDeclineChanges(id, t, actions.getNotes(), recordName);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_DECLINED);
                } else if (action.equalsIgnoreCase(AppConstants.ACTIVITY_DELETE)
                        && actionStatus.equalsIgnoreCase(AppConstants.STATUS_UNAPPROVED)) {
                    this.processDeclineDeletion(id, t, actions.getNotes(), recordName);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_DECLINED);
                } else if (action.equalsIgnoreCase(AppConstants.ACTIVITY_CREATE)
                        && actionStatus.equalsIgnoreCase(AppConstants.STATUS_UNCONFIRMED)) {
                    this.processDeclineConfirmation(id, t, actions.getNotes(), recordName);
                    accessor.setPropertyValue("intrash", AppConstants.YES);
                    accessor.setPropertyValue("actionStatus", AppConstants.STATUS_DECLINED);
                } else {
                    loggerService.log("Failed to decline " + recordName + ". Record doesn't have approve actions",
                            clazz.getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    errors.add("Record doesn't have approve actions");
                }
                this.entityManager.merge(t);
            } catch (ExpectationFailed ex) {
                errors.add(ex.getMessage());
            }
        }
        if (errors.isEmpty()) {
            return ResponseEntity.ok(response);
        } else {
            response.setCode(HttpStatus.MULTI_STATUS.value());
            response.setData(errors);
            response.setMessage(AppConstants.CHECKER_GENERAL_ERROR);
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
        }
    }

    /**
     * Decline new records
     *
     * param id ID of the entity being declined
     * param entity the entity to be merged with the changes
     * param notes decline notes for the audit trail
     * param nickName entity meaningful name
     * throws ExpectationFailed thrown when either fields annotated with
     * {link ModifiableField} cannot be accessed or when data stored in edited
     * entity cannot be mapped back to an entity
     */
    protected void processDeclineNew(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        loggerService.log("Declined new " + nickName + "",
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * Decline edit changes. Clears temporal changes stored in the entity record
     *
     * param id ID of the entity being declined
     * param entity the entity to be merged with the changes
     * param notes approve notes for the audit trail
     * param nickName entity meaningful name
     * throws ExpectationFailed thrown when either fields annotated with
     * @{link ModifiableField} cannot be accessed or when data stored in edited
     * entity cannot be mapped back to an entity
     */
    protected void processDeclineChanges(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        try {
            supportRepo.declineChanges(id);
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            log.error(AppConstants.AUDIT_LOG, "Failed to decline record changes", ex);
            throw new ExpectationFailed("Failed to decline record changes please contact the administrator for more help");
        }
        loggerService.log("Done declining " + nickName + " changes",
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_UPDATE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * Decline Deletion
     *
     * param id ID of the entity being declined
     * param entity the entity to be merged with the changes
     * param notes approve notes for the audit trail
     * param nickName entity meaningful name
     * throws ExpectationFailed thrown when either fields annotated with
     * {link ModifiableField} cannot be accessed or when data stored in edited
     * entity cannot be mapped back to an entity
     */
    protected void processDeclineDeletion(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        loggerService.log("Done declining " + nickName + " deletion.",
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * param id
     * param entity
     * param notes
     * param nickName
     * throws ExpectationFailed
     */
    protected void processDeclineConfirmation(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        loggerService.log("Declined ocnfirmation " + nickName + ".",
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * Used to fetch entity updated changes
     *
     * param id entity id to be effected
     * return {link ResponseEntity} with status 200 and a {link List} of
     * changes (Returns an empty list if changes don't exist)
     * throws java.lang.IllegalAccessException if a field on the entity could
     * not be accessed
     * throws java.io.IOException if stored changes could not be read
     */
    @RequestMapping(method = RequestMethod.GET, value = "/{id}/changes")
    @ApiOperation(value = "Fetch Record Changes")
    public ResponseEntity<ResponseWrapper<List<String>>> fetchChanges(@PathVariable("id") E id) throws IllegalAccessException, IOException {
        ResponseWrapper<List<String>> response = new ResponseWrapper();
        response.setData(supportRepo.fetchChanges(id, this.fetchEntity(id)));
        return ResponseEntity.ok(response);
    }

    /**
     * Used to validate unique fields in a given entity against existing records
     * in the database. Unique fields are identified using @{link Unique}
     * annotation.
     *
     * param t Entity to be validated
     * throws RuntimeException If the current field doesn't have an id field
     * (Field annotated with @{link Id})
     * throws GeneralBadRequest If unique validation fails on a field annotated
     * with @{link Unique} annotation
     */
    private void validateUniqueFields(T t) throws GeneralBadRequest {

        //Declare properties
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(t);
        Class clazz = SharedMethods.getGenericClasses(this.getClass()).get(0);
        String fieldId = null;
        E id = null;
        boolean hasIntrash = false;
        List<Field> uniqueFields = new ArrayList<>();
        CriteriaBuilder criteriaBuilder = this.entityManager.getCriteriaBuilder();

        //Retrieve annotated fields and values
        for (Field field : t.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Unique.class)) {
                uniqueFields.add(field);
            }
            if (field.isAnnotationPresent(Id.class)) {
                fieldId = field.getName();
                id = (E) accessor.getPropertyValue(field.getName());
            }
            if (field.getName().equalsIgnoreCase("intrash")) {
                hasIntrash = true;
            }
        }

        //check if id field is present
        if (fieldId == null) {
            throw new RuntimeException("Failed to validate unique fields. Entity doesn't have an id field");
        }

        //validate unique fields
        for (Field field : uniqueFields) {
            CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(clazz);
            Root<T> root = criteriaQuery.from(clazz);
            Unique unique = field.getDeclaredAnnotation(Unique.class);
            Object value = accessor.getPropertyValue(field.getName());
            
            if (hasIntrash) {
                if (id == null) {
                    criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.and(criteriaBuilder.equal(root.get(field.getName()), value),
                            criteriaBuilder.equal(root.get("intrash"), AppConstants.NO))));
                } else {
                    criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.and(criteriaBuilder.equal(root.get(field.getName()), value),
                            criteriaBuilder.equal(root.get("intrash"), AppConstants.NO)), criteriaBuilder.notEqual(root.get(fieldId), id)));
                }
            } else {
                if (id == null) {
                    criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.equal(root.get(field.getName()), value)));
                } else {
                    criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.equal(root.get(field.getName()), value), criteriaBuilder.notEqual(root.get(fieldId), id)));
                }
            }
            if (!this.entityManager.createQuery(criteriaQuery).getResultList().isEmpty()) {
                throw new GeneralBadRequest("Record with similar " + unique.fieldName() + " exists");
            }
        }
    }

    /**
     * Fetch entity excluding entities in trash
     *
     * param id
     * return
     */
    public T fetchEntity(Serializable id) {
        Class clazz = this.genericClasses.get(0);//Shar.getGenericClasses(this.getClass()).get(0);
        //get id field name
        String fieldId = null;
        boolean hasIntrash = false;
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                fieldId = field.getName();
            }
            if (field.getName().equalsIgnoreCase("intrash")) {
                hasIntrash = true;
            }
        }
        
        if (fieldId == null) {
            throw new RuntimeException("Entity doesn't have an id field");
        }
        
        CriteriaBuilder criteriaBuilder = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(clazz);
        Root<T> root = criteriaQuery.from(clazz);
        if (hasIntrash) {
            criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.equal(root.get(fieldId), id),
                    criteriaBuilder.equal(root.get("intrash"), AppConstants.NO)));
        } else {
            criteriaQuery.where(criteriaBuilder.equal(root.get(fieldId), id));
        }
        try {
            return this.entityManager.createQuery(criteriaQuery).getSingleResult();
        } catch (javax.persistence.NoResultException ex) {
            return null;
        }
    }

    /**
     * Used to retrieve all entity records.
     * <h4>Note</h4>
     * <ul>
     * <li>If needle parameter is present search will be done on fields
     * annotated with @{link Searchable} (Search is case insensitive)</li>
     * <li>If fields annotated with @{link Filter} exist the request will be
     * searched for parameters with similar name as the field name and if found
     * the results will be filtered using the filter. For example for field
     * <pre>@Filter private String name;</pre> expects the request name
     * parameter to be name. To filter by date range you need to provide to and
     * from request parameters with a valid String date (dd/MM/yyyy, dd/MM/yyyy
     * HH:mm:ss.SSS, dd/MM/yyyy HH:mm:ss)</li>
     * </ul>
     *
     * param pg used to sort and limit the result
     * param request HTTP Request used to get filter and search parameters.
     * return
     * throws ParseException if request param date cannot be casted to
     * {link Date}
     */
    @ApiOperation(value = "Fetch all Records", notes = "")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "size", dataType = "integer", required = false, value = "Pagination size e.g 20", paramType = "query")
        ,
            @ApiImplicitParam(name = "page", dataType = "integer", required = false, value = "Page number e.g 0", paramType = "query")
        ,
            @ApiImplicitParam(name = "sort", dataType = "string", required = false, value = "Field name e.g actionStatus,asc/desc", paramType = "query")
    })
    @GetMapping
    public ResponseEntity<ResponseWrapper<Page<T>>> findAll(Pageable pg, HttpServletRequest request) throws ParseException {
        
        ResponseWrapper response = new ResponseWrapper();
        CriteriaBuilder criteriaBuilder = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(this.genericClasses.get(0));
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<T> root = criteriaQuery.from(this.genericClasses.get(0));
        ArrayList<Predicate> searchPreds = new ArrayList<>();
        ArrayList<Predicate> filterPreds = new ArrayList<>();
        List<Order> ords = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        Calendar cal = Calendar.getInstance();

        //retrieve filter and search params
        for (Field field : this.genericClasses.get(0).getDeclaredFields()) {
            if (field.isAnnotationPresent(Searchable.class) && request.getParameter("needle") != null) { //process search attributes
                searchPreds.add(criteriaBuilder.like(criteriaBuilder.upper(root.get(field.getName())),
                        "%" + request.getParameter("needle").toUpperCase() + "%"));
            }
            
            if (field.isAnnotationPresent(Filter.class)) {//process filter attributes
                if (field.getAnnotation(Filter.class).isDateRange() && request.getParameter("to") != null
                        && request.getParameter("from") != null) {//filter date range

                    Date from = this.tryParse(request.getParameter("from"));
                    if (from == null) {
                        throw new ParseException("Failed to parse " + request.getParameter("from") + " to date", 0);
                    }
                    cal.setTime(from);
                    cal.add(Calendar.DAY_OF_WEEK, -1);
                    from = cal.getTime();
                    
                    Date to = this.tryParse(request.getParameter("to"));
                    if (to == null) {
                        throw new ParseException("Failed to parse " + request.getParameter("to") + " to date", 0);
                    }
                    cal.setTime(to);
                    cal.add(Calendar.DAY_OF_WEEK, 1);
                    to = cal.getTime();
                    Predicate datePred = criteriaBuilder.between(root.get(field.getName()).as(Date.class), from, to);
                    filterPreds.add(datePred);
                } else if (request.getParameter(field.getName()) != null && !request.getParameter(field.getName()).isEmpty()) {
                    if (field.isAnnotationPresent(ManyToOne.class)) {
                        BeanWrapper wrapper = new BeanWrapperImpl(field.getType());
                        for (Field f : field.getType().getDeclaredFields()) {
                            if (f.isAnnotationPresent(Id.class)) {
                                wrapper.setPropertyValue(f.getName(), request.getParameter(field.getName()));
                                break;
                            }
                        }
                        filterPreds.add(criteriaBuilder.equal(root.get(field.getName()), wrapper.getWrappedInstance()));
                    } else {
                        filterPreds.add(criteriaBuilder.like(root.get(field.getName()).as(String.class),
                                request.getParameter(field.getName())));
                    }
                } else {
                    log.debug("Failed to find parameter {} skipping filtering for field {} ", field.getName(), field.getName());
                }
            }
            //check if order parameter is available for the current field
            Sort.Order ord = pg.getSort().getOrderFor(field.getName());
            if (ord != null) {
                log.debug("Found ordering paramater ({}) for field {} preparing ordering query", ord, field.getName());
                if (ord.isAscending()) {
                    ords.add(criteriaBuilder.asc(root.get(field.getName())));
                } else {
                    ords.add(criteriaBuilder.desc(root.get(field.getName())));
                }
            }
            //check if is intrash
            if (field.getName().equalsIgnoreCase("intrash")) {
                filterPreds.add(criteriaBuilder.equal(root.get(field.getName()), AppConstants.NO));
            }
            
            if (field.isAnnotationPresent(TreeRoot.class)) {
                filterPreds.add(criteriaBuilder.isNull(root.get(field.getName())));
            }
        }
        
        if (!filterPreds.isEmpty() && !searchPreds.isEmpty()) {
            criteriaQuery.where(criteriaBuilder.and(filterPreds.toArray(new Predicate[filterPreds.size()])),
                    criteriaBuilder.or(searchPreds.toArray(new Predicate[searchPreds.size()])));
            countQuery.where(criteriaBuilder.and(filterPreds.toArray(new Predicate[filterPreds.size()])),
                    criteriaBuilder.or(searchPreds.toArray(new Predicate[searchPreds.size()])));
        } else if (!filterPreds.isEmpty()) {
            criteriaQuery.where(filterPreds.toArray(new Predicate[filterPreds.size()]));
            countQuery.where(filterPreds.toArray(new Predicate[filterPreds.size()]));
        } else if (!searchPreds.isEmpty()) {
            criteriaQuery.where(criteriaBuilder.or(searchPreds.toArray(new Predicate[searchPreds.size()])));
            countQuery.where(criteriaBuilder.or(searchPreds.toArray(new Predicate[searchPreds.size()])));
        } else {
            criteriaQuery.where();
            countQuery.where();
        }
        
        criteriaQuery.orderBy(ords);
        List<T> content = this.entityManager
                .createQuery(criteriaQuery)
                .setFirstResult((pg.getPageNumber() * pg.getPageSize()))
                .setMaxResults(pg.getPageSize())
                .getResultList();
        
        countQuery.select(criteriaBuilder.count(countQuery.from(this.genericClasses.get(0))));
        Long total = this.entityManager.createQuery(countQuery).getSingleResult();
        
        Page<T> page = new PageImpl<>(content, pg, total);
        
        response.setData(page);
        return ResponseEntity.ok(response);
    }

    /**
     * param dateString
     * return
     */
    private Date tryParse(String dateString) {
        List<String> formatStrings = Arrays.asList(
                "dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy",
                "dd/MM/yyyy HH:mm:ss.SSS", "dd/MM/yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm:ss.SSS", "dd-MM-yyyy HH:mm:ss");
        for (String formatString : formatStrings) {
            try {
                return new SimpleDateFormat(formatString).parse(dateString);
            } catch (ParseException e) {
            }
        }
        
        return null;
    }
    
    class ExcludeProxiedFields implements ExclusionStrategy {
        
        @Override
        public boolean shouldSkipField(FieldAttributes fa) {
            return fa.getAnnotation(ManyToOne.class) != null
                    || fa.getAnnotation(OneToOne.class) != null
                    || fa.getAnnotation(ManyToMany.class) != null
                    || fa.getAnnotation(OneToMany.class) != null;
        }
        
        @Override
        public boolean shouldSkipClass(Class<?> type) {
            return false;
        }
    }
    
}
