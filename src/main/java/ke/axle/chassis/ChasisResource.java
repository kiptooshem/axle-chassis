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
import ke.axle.chassis.entity.base.StandardEntity;
import ke.axle.chassis.entity.enums.MakerCheckerAction;
import ke.axle.chassis.entity.enums.MakerCheckerActionStatus;
import ke.axle.chassis.exceptions.ExpectationFailed;
import ke.axle.chassis.exceptions.GeneralBadRequest;
import ke.axle.chassis.service.CreateEntityService;
import ke.axle.chassis.utils.*;
import ke.axle.chassis.wrappers.ActionWrapper;
import ke.axle.chassis.wrappers.ResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Exposes the following resource actions;
 * Create new resource
 * Update existing resource
 * Delete existing resource
 * Approve checker actions
 * Decline checker actions
 * Fetch, filter, search and paginate resources
 * <p>
 * param <T> action entity
 * param <E> id class
 * param <R> Edited entity
 *
 * @author Cornelius M
 * @author Owori Juma
 * @version 1.2.3
 */

@Component
public class ChasisResource<T extends StandardEntity, E extends Serializable, R> {

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

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    @Autowired
    private CreateEntityService createEntityService;

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
        //this.genericClasses = SharedMethods.getGenericClasses(this.getClass());
        this.genericClasses = Arrays.asList((Class<T>) (Class) this.getClass(),(Class<E>) (Class) this.getClass(), (Class<R>) (Class) this.getClass());
        this.supportRepo = new SupportRepository<>(entityManager, this.genericClasses.get(0), this.genericClasses.get(2));
        NickName nickName = AnnotationUtils.findAnnotation(this.genericClasses.get(0), NickName.class);
        this.recordName = (nickName == null) ? "Record" : nickName.name();
        log.debug("Assigned record name {} to entity {}", this.recordName, this.genericClasses.get(0));
    }

    /**
     * Used to persist new entities to the database. The following validations
     * are carried out before an entity is persisted:
     * If an id field is present on the entity it will be reset to null.
     * 201 on success
     * 409 on unique validation error
     * 400 on validation error
     * 404 for {link ManyToOne} entities that don't exist
     */
    @RequestMapping(method = RequestMethod.POST)
    @Transactional
    @ApiOperation(value = "Create New Record")
    public ResponseEntity<ResponseWrapper<T>> create(@Valid @RequestBody T t) {
        ResponseWrapper response = new ResponseWrapper();

        try {
            t = createEntityService.create(this, t);

        } catch (GeneralBadRequest generalBadRequest) {
            generalBadRequest.printStackTrace();
            response.setCode(generalBadRequest.getHttpStatus().value());
            response.setMessage(generalBadRequest.getMessage());

            return new ResponseEntity<>(response, generalBadRequest.getHttpStatus());
        }

        response.setData(t);
        response.setCode(201);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Used to fetch entity by id
     * <p>
     * param id Entity id
     * return {link ResponseEntity} with data field containing the entity
     * (data is null when entity could not be found) and status 200:
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ApiOperation(value = "Fetch single record using record id")
    public ResponseEntity<ResponseWrapper<T>> getEntity(@PathVariable("id") E id) {
        T dbT = this.fetchEntity(id);
        String extra = this.getLogsExtraDescription(dbT);

        loggerService.log("Viewed " + recordName + extra,
                this.genericClasses.get(0).getSimpleName(), id, AppConstants.ACTIVITY_VIEW, AppConstants.STATUS_COMPLETED, "");
        ResponseWrapper response = new ResponseWrapper();
        response.setData(dbT);
        return ResponseEntity.ok(response);
    }

    /**
     * Used to update entities by saving new changes to the edited record
     * entity. For edited record to work The following annotation must be
     * present on the relevant fields to be used to store changes;
     * For created and updated records that have not been approved the changes
     * are persisted to the entity directly without being stored in the edited
     * record entity
     * <p>
     * param t entity containing new changes
     * return {link ResponseEntity} with statuses:
     * <p>
     * throws IllegalAccessException if a field on the entity could not be
     * accessed
     * throws JsonProcessingException if changes could not be converted to json
     * string
     * throws
     * ExpectationFailed When
     * editEntity does not have fields; @{link EditEntity}, @{link EditEntity}
     * and {link EditEntityId}
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
        String beforeAndAfter = "";

        T dbT = this.fetchEntity(t.getId());
        beforeAndAfter += supportRepo.getBeforeAndAfterValues(dbT, t);
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

        if (dbT.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED && (dbT.getAction() == MakerCheckerAction.CREATE || dbT.getAction() == MakerCheckerAction.UPDATE)) {
            loggerService.log("Updating " + recordName + " failed due to record has unapproved actions",
                    t.getClass().getSimpleName(), SharedMethods.getEntityIdValue(t),
                    AppConstants.ACTIVITY_CREATE, AppConstants.STATUS_FAILED, "");
            response.setCode(HttpStatus.EXPECTATION_FAILED.value());
            response.setMessage("Sorry record has Unapproved actions");
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(response);
        }

        if (dbT.getActionStatus() != MakerCheckerActionStatus.UNAPPROVED) {
            dbT.setAction(MakerCheckerAction.UPDATE);
            dbT.setActionStatus(MakerCheckerActionStatus.UNAPPROVED);
        }

        t.setUpdateTs(new Date());
        List<String> changes;
        if (dbT.getAction() == MakerCheckerAction.CREATE && dbT.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED) {
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

        String extra = this.getLogsExtraDescription(t);
        response.setData(t);

        String notes = " Data Changes: " + beforeAndAfter;

        loggerService.log("Updated " + recordName + " successfully. " + extra
                        + String.join(",", changes) + ". Detail Changes: " + beforeAndAfter,
                t.getClass().getSimpleName(), SharedMethods.getEntityIdValue(t),
                AppConstants.ACTIVITY_UPDATE, AppConstants.STATUS_COMPLETED, "");

        return ResponseEntity.ok(response);
    }

    /**
     * Used to delete entities.
     * Note
     * If action and actionStatus fields don't exist the record is moved to
     * trash directly (flagging .
     * If intrash field doesn't exist the record is deleted permanently
     * <p>
     * param actions contains an array of entity id(s)
     * return {link ResponseEntity} with statuses:
     * 200 on success
     * 207 if not all records could be deleted
     * 404 if the entity doesn't exist in the database<
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

            try {
                if (t.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED) {
                    loggerService.log("Failed to delete " + recordName + ". Record has unapproved actions",
                            t.getClass().getSimpleName(), id, AppConstants.ACTIVITY_DELETE, AppConstants.STATUS_FAILED, "");
                    errors.add("Record has unapproved actions");
                } else {
                    t.setAction(MakerCheckerAction.DELETE);
                    t.setActionStatus(MakerCheckerActionStatus.UNAPPROVED);
                    t.setDeleteTs(new Date());
                    t.setUpdateTs(new Date());

                    this.entityManager.persist(t);
                    String extra = this.getLogsExtraDescription(t);
                    loggerService.log("Deleted " + recordName + " successfully." + extra, this.genericClasses.get(0).getSimpleName(),
                            id, AppConstants.ACTIVITY_DELETE, AppConstants.STATUS_COMPLETED, "");
                }
            } catch (org.springframework.beans.NotWritablePropertyException e) {
                log.debug("Failed to find action and action status failed skipping "
                        + "updating action status proceeding to flag intrash field");
                try {
                    t.setDeleteTs(new Date());
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
     * return {link ResponseEntity} with statuses:     *
     * 200 on success
     * 404 if the entity doesn't exist in the database<
     * 207 if some of the action could be approved successfuly. The data
     * fields contains more details on records that failed
     * throws
     * ExpectationFailed When
     * entity doesn't have action or actionStatus fields
     */
    @RequestMapping(value = "/approve-actions", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<ResponseWrapper> approveActions(@RequestBody @Valid ActionWrapper<E> actions) throws ExpectationFailed {
        ResponseWrapper response = new ResponseWrapper();
        List<String> errors = new ErrorList();
        List<E> success = new ArrayList<>();

        for (E id : actions.getIds()) {
            T t = this.fetchEntity(id);
            try {
                if (t == null) {
                    loggerService.log("Failed to approve " + recordName + ". Failed to locate record with specified id",
                            this.genericClasses.get(0).getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    errors.add(recordName + " with id " + id + " doesn't exist");
                    continue;
                }

                if (loggerService.isInitiator(this.genericClasses.get(0).getSimpleName(), id, t.getAction().toString()) && t.getActionStatus() != MakerCheckerActionStatus.UNCONFIRMED) {
                    errors.add("Sorry failed to approve " + recordName + ". Maker can't approve their own record ");
                    loggerService.log("Failed to approve " + recordName + ". Maker can't approve their own record",
                            SharedMethods.getEntityName(this.genericClasses.get(0)), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    continue;
                } else if (t.getAction() == MakerCheckerAction.CREATE
                        && t.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED) {//process new record
                    this.processApproveNew(id, t, actions.getNotes(), recordName);
                    t.setActionStatus(MakerCheckerActionStatus.APPROVED);
                } else if (t.getAction() == MakerCheckerAction.UPDATE
                        && t.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED) {//process updated record
                    this.processApproveChanges(id, t, actions.getNotes(), recordName);
                    t.setActionStatus(MakerCheckerActionStatus.APPROVED);
                } else if (t.getAction() == MakerCheckerAction.DELETE
                        && t.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED) {
                    this.processApproveDeletion(id, t, actions.getNotes(), recordName);
                    t.setActionStatus(MakerCheckerActionStatus.APPROVED);
                } else if (t.getAction() == MakerCheckerAction.CREATE
                        && t.getActionStatus() == MakerCheckerActionStatus.UNCONFIRMED) {
                    this.processConfirm(id, t, actions.getNotes(), recordName);
                    t.setActionStatus(MakerCheckerActionStatus.UNAPPROVED);
                } else {
                    loggerService.log("Failed to approve " + recordName + ". Record doesn't have approve actions",
                            this.genericClasses.get(0).getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    errors.add(recordName + " with id " + id + " doesn't have approve actions");
                }
                this.entityManager.merge(t);
                success.add(id);
            } catch (ExpectationFailed ex) {
                errors.add(ex.getMessage());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("errors", errors);
        response.setData(data);

        if (errors.isEmpty()) {
            return ResponseEntity.ok(response);
        } else {
            response.setCode(HttpStatus.MULTI_STATUS.value());
            response.setMessage(AppConstants.CHECKER_GENERAL_ERROR);
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
        }
    }


    protected void processApproveNew(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        String extra = this.getLogsExtraDescription(entity);
        loggerService.log("Done approving new  " + nickName + " " + extra,
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }


    protected void processApproveChanges(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        try {
            List<String> changes = supportRepo.fetchChanges(id, this.fetchEntity(id));
            notes += " Data Changes: " + String.join(",", changes);
            entity = supportRepo.mergeChanges(id, entity);
        } catch (IOException | IllegalArgumentException | IllegalAccessException ex) {
            log.error(AppConstants.AUDIT_LOG, "Failed to approve record changes", ex);
            throw new ExpectationFailed("Failed to approve record changes please contact the administrator for more help");
        }
        String extra = this.getLogsExtraDescription(entity);
        loggerService.log("Done approving " + nickName + " changes " + extra,
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }


    protected void processApproveDeletion(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        entity.setDeleteTs(new Date());
        String extra = this.getLogsExtraDescription(entity);
        loggerService.log("Done approving " + nickName + " deletion " + extra,
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }


    protected void processConfirm(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        String extra = this.getLogsExtraDescription(entity);
        loggerService.log("Done confirmation " + nickName + " " + extra,
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * Used to decline actions (create, update, delete, deactivate). Ensures
     * only the checker can decline an action
     * <p>
     * param actions
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

        List<E> success = new ArrayList<>();

        for (E id : actions.getIds()) {
            T t = supportRepo.fetchEntity(id);
            try {
                if (t == null) {
                    loggerService.log("Failed to decline " + recordName + ". Failed to locate record with specified id",
                            clazz.getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    errors.add(recordName + " with id " + id + " doesn't exist");
                    continue;
                }

                if (loggerService.isInitiator(clazz.getSimpleName(), id, t.getAction().toString()) && t.getActionStatus() != MakerCheckerActionStatus.UNCONFIRMED) {
                    errors.add("Sorry maker can't approve their own record ");
                    loggerService.log("Failed to approve " + recordName + ". Maker can't approve their own record",
                            SharedMethods.getEntityName(clazz), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    continue;
                } else if (t.getAction() == MakerCheckerAction.CREATE
                        && t.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED) {//process new record
                    this.processDeclineNew(id, t, actions.getNotes(), recordName);
                    t.setActionStatus(MakerCheckerActionStatus.DECLINED);

                    t.setDeleteTs(new Date());

                } else if (t.getAction() == MakerCheckerAction.UPDATE
                        && t.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED) {//process updated record
                    this.processDeclineChanges(id, t, actions.getNotes(), recordName);

                    t.setActionStatus(MakerCheckerActionStatus.DECLINED);

                } else if (t.getAction() == MakerCheckerAction.DELETE
                        && t.getActionStatus() == MakerCheckerActionStatus.UNAPPROVED) {
                    this.processDeclineDeletion(id, t, actions.getNotes(), recordName);
                    t.setActionStatus(MakerCheckerActionStatus.DECLINED);
                } else if (t.getAction() == MakerCheckerAction.CREATE
                        && t.getActionStatus() == MakerCheckerActionStatus.UNCONFIRMED) {
                    this.processDeclineConfirmation(id, t, actions.getNotes(), recordName);
                    t.setDeleteTs(new Date());
                    t.setActionStatus(MakerCheckerActionStatus.DECLINED);
                } else {
                    loggerService.log("Failed to decline " + recordName + ". Record doesn't have approve actions",
                            clazz.getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_FAILED, actions.getNotes());
                    errors.add("Record doesn't have approve actions");
                }
                this.entityManager.merge(t);
                success.add(id);
            } catch (ExpectationFailed ex) {
                errors.add(ex.getMessage());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("errors", errors);
        response.setData(data);

        if (errors.isEmpty()) {
            return ResponseEntity.ok(response);
        } else {
            response.setCode(HttpStatus.MULTI_STATUS.value());
            response.setMessage(AppConstants.CHECKER_GENERAL_ERROR);
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
        }
    }

    /**
     * Decline new records
     * <p>
     * param id ID of the entity being declined
     * param entity the entity to be merged with the changes
     * param notes decline notes for the audit trail
     * param nickName entity meaningful name
     * throws ExpectationFailed thrown when either fields annotated with
     * {link ModifiableField} cannot be accessed or when data stored in edited
     * entity cannot be mapped back to an entity
     */
    protected void processDeclineNew(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        String extra = this.getLogsExtraDescription(entity);
        loggerService.log("Declined new " + nickName + " " + extra,
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * Decline edit changes. Clears temporal changes stored in the entity record
     * <p>
     * param id ID of the entity being declined
     * param entity the entity to be merged with the changes
     * param notes approve notes for the audit trail
     * param nickName entity meaningful name
     * throws ExpectationFailed thrown when either fields annotated with
     *
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
        String extra = this.getLogsExtraDescription(entity);
        loggerService.log("Done declining " + nickName + " changes " + extra,
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_UPDATE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * Decline Deletion
     * <p>
     * param id ID of the entity being declined
     * param entity the entity to be merged with the changes
     * param notes approve notes for the audit trail
     * param nickName entity meaningful name
     * throws ExpectationFailed thrown when either fields annotated with
     * {link ModifiableField} cannot be accessed or when data stored in edited
     * entity cannot be mapped back to an entity
     */
    protected void processDeclineDeletion(E id, T entity, String notes, String nickName) throws ExpectationFailed {
        String extra = this.getLogsExtraDescription(entity);
        loggerService.log("Done declining " + nickName + " deletion " + extra,
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
        String extra = this.getLogsExtraDescription(entity);
        loggerService.log("Declined Confirmation " + nickName + " " + extra,
                entity.getClass().getSimpleName(), id, AppConstants.ACTIVITY_APPROVE, AppConstants.STATUS_COMPLETED, notes);
    }

    /**
     * Used to fetch entity updated changes
     * <p>
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
        ResponseWrapper<List<String>> response = new ResponseWrapper<>();
        response.setData(supportRepo.fetchChanges(id, this.fetchEntity(id)));
        return ResponseEntity.ok(response);
    }

    /**
     * Used to validate unique fields in a given entity against existing records
     * in the database. Unique fields are identified using @{link Unique}
     * annotation.
     * <p>
     * param t Entity to be validated
     * throws RuntimeException If the current field doesn't have an id field
     * (Field annotated with {link Id})
     * throws GeneralBadRequest If unique validation fails on a field annotated
     * with {link Unique} annotation
     */
    private void validateUniqueFields(T t) throws GeneralBadRequest {

        //Declare properties
        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(t);
        //Class clazz = SharedMethods.getGenericClasses(this.getClass()).get(0);
        //System.out.println("Validation; Chasis class: " + clazz);

        Class<T> clazz = (Class<T>) t.getClass(); //This line and the previous should produce the same thing

        String fieldId = "id";
        String id = t.getId();
        List<Field> uniqueFields = new ArrayList<>();
        CriteriaBuilder criteriaBuilder = this.entityManager.getCriteriaBuilder();

        //Retrieve annotated fields and values
        for (Field field : Extractor.getAllFields(t.getClass())) {
            if (field.isAnnotationPresent(Unique.class)) {
                uniqueFields.add(field);
            }

        }

        //validate unique fields
        for (Field field : uniqueFields) {
            CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(clazz);
            Root<T> root = criteriaQuery.from(clazz);
            Unique unique = field.getDeclaredAnnotation(Unique.class);
            Object value = accessor.getPropertyValue(field.getName());

            if (id == null) {
                criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.and(criteriaBuilder.equal(root.get(field.getName()), value),
                        criteriaBuilder.isNull(root.get("deleteTs")))));
            } else {
                criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.and(criteriaBuilder.equal(root.get(field.getName()), value),
                        criteriaBuilder.isNull(root.get("deleteTs"))), criteriaBuilder.notEqual(root.get(fieldId), id)));
            }

            if (!this.entityManager.createQuery(criteriaQuery).getResultList().isEmpty()) {
                throw new GeneralBadRequest("Record with similar " + unique.fieldName() + " exists");
            }
        }
    }

    /**
     * Fetch entity excluding entities in trash
     * <p>
     * param id
     * return
     */
    public T fetchEntity(Serializable id) {
        Class<T> clazz = (Class<T>) this.genericClasses.get(0);//Shar.getGenericClasses(this.getClass()).get(0);

        CriteriaBuilder criteriaBuilder = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(clazz);
        Root<T> root = criteriaQuery.from(clazz);


        criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.equal(root.get("id"), id),
                criteriaBuilder.isNull(root.get("deleteTs"))));

        /*if (hasIntrash) {
            criteriaQuery.where(criteriaBuilder.and(criteriaBuilder.equal(root.get(fieldId), id),
                    criteriaBuilder.equal(root.get("intrash"), AppConstants.NO)));
        } else {
            criteriaQuery.where(criteriaBuilder.equal(root.get(fieldId), id));
        }*/
        try {
            return this.entityManager.createQuery(criteriaQuery).getSingleResult();
        } catch (javax.persistence.NoResultException ex) {
            return null;
        }
    }

    /**
     * Used to retrieve all entity records.
     * <p>
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
        return fetchAllData(pg, request, false);
    }


    /**
     * Used to retrieve all deleted entity records.
     * <p>
     * param pg used to sort and limit the result
     * param request HTTP Request used to get filter and search parameters.
     * return
     * throws ParseException if request param date cannot be casted to
     * {link Date}
     */
    @ApiOperation(value = "Fetch all Deleted Records", notes = "")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "size", dataType = "integer", required = false, value = "Pagination size e.g 20", paramType = "query")
            ,
            @ApiImplicitParam(name = "page", dataType = "integer", required = false, value = "Page number e.g 0", paramType = "query")
            ,
            @ApiImplicitParam(name = "sort", dataType = "string", required = false, value = "Field name e.g actionStatus,asc/desc", paramType = "query")
    })
    @GetMapping(value = "/deleted")
    public ResponseEntity<ResponseWrapper<Page<T>>> findAllDeleted(Pageable pg, HttpServletRequest request) throws ParseException {
        return fetchAllData(pg, request, true);
    }

    private ResponseEntity<ResponseWrapper<Page<T>>> fetchAllData(Pageable pg, HttpServletRequest request, boolean inTrash) throws ParseException {
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
        for (Field field : Extractor.getAllFields(this.genericClasses.get(0))) {
            if (field.isAnnotationPresent(Searchable.class) && request.getParameter("needle") != null) { //process search attributes
                searchPreds.add(criteriaBuilder.like(criteriaBuilder.upper(root.get(field.getName())),
                        "%" + request.getParameter("needle").toUpperCase() + "%"));
            }

            if (field.isAnnotationPresent(Filter.class)) {//process filter attributes
                if (field.getAnnotation(Filter.class).isDateRange() && request.getParameter("to") != null
                        && request.getParameter("from") != null) {//filter date range

                    Date fromDate = this.tryParse(request.getParameter("from"));
                    if (fromDate == null) {
                        throw new ParseException("Failed to parse " + request.getParameter("from") + " to date", 0);
                    }
                    cal.setTime(fromDate);
                    fromDate = cal.getTime();

                    LocalDateTime fromTime = fromDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().withHour(0).withMinute(0).withSecond(0).withNano(0);
                    Date from = Date.from(fromTime.atZone(ZoneId.systemDefault()).toInstant());

                    Date toDate = this.tryParse(request.getParameter("to"));
                    if (toDate == null) {
                        throw new ParseException("Failed to parse " + request.getParameter("to") + " to date", 0);
                    }
                    cal.setTime(toDate);
                    toDate = cal.getTime();

                    LocalDateTime toTime = toDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().withHour(23).withMinute(59).withSecond(59).withNano(0);
                    Date to = Date.from(toTime.atZone(ZoneId.systemDefault()).toInstant());

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
            if (inTrash) {
                filterPreds.add(criteriaBuilder.isNotNull(root.get("deleteTs")));
            }else{
                filterPreds.add(criteriaBuilder.isNull(root.get("deleteTs")));
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

    private String getLogsExtraDescription(T t) {
        Object entityName = null;
        String name = null;

        if (t == null) return "";

        PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(t);
        for (Field field : Extractor.getAllFields(t.getClass())) {
            if (field.isAnnotationPresent(EntityName.class)) {
                entityName = accessor.getPropertyValue(field.getName());
                name = field.getName();
                break;
            }
        }
        return (entityName != null) ? " with " + name + " :" + entityName : "";
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
