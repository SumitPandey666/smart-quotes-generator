package com.smartquotes.quoteservice.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // This annotation can only be put on methods
@Retention(RetentionPolicy.RUNTIME) // It must be available at runtime for AOP to intercept it
public @interface TrackExecutionTime {
}