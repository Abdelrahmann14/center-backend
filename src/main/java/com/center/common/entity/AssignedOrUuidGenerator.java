package com.center.common.entity;

import java.util.EnumSet;
import java.util.UUID;

import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

/**
 * Honours an id the caller set; mints a random UUID when it is null.
 *
 * <p>Behaviour is unchanged for every existing call site: they all build a fresh
 * entity whose id is null, so they still get a generated UUID and Spring Data
 * still sees a new entity. Only a caller that deliberately assigns an id first -
 * the offline sync writer replaying a row the client already created - is
 * affected, and for it keeping that id is the whole point.
 */
public class AssignedOrUuidGenerator implements BeforeExecutionGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner,
            Object currentValue, EventType eventType) {
        return currentValue != null ? currentValue : UUID.randomUUID();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
