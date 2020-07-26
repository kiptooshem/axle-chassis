/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cm.projects.spring.resource.chasis;

import ke.axle.chassis.annotations.Filter;
import ke.axle.chassis.annotations.ModifiableField;
import ke.axle.chassis.annotations.NickName;
import ke.axle.chassis.annotations.Searchable;
import ke.axle.chassis.annotations.Unique;
import ke.axle.chassis.entity.base.StandardEntity;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

/**
 *
 * @author Cornelius M
 */
@Entity
@NickName(name = "Developer")
public class Nerd extends StandardEntity {

    @Unique(fieldName = "Name")
    @Searchable
    private String name;
    @ModifiableField
    @Searchable
    private String expertise;

    @Filter
    @ManyToOne
    @JoinColumn(name = "gender_id")
    private Gender gender;

    public Nerd() {
    }

    public Nerd(String name, String expertise) {
        this.name = name;
        this.expertise = expertise;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExpertise() {
        return expertise;
    }

    public void setExpertise(String expertise) {
        this.expertise = expertise;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    @Override
    public String toString() {
        return "Nerd{" + "id=" + id + ", name=" + name + ", expertise=" + expertise + ", action=" + action + ", actionStatus=" + actionStatus + ", intrash=" + isDeleted() + '}';
    }

}
