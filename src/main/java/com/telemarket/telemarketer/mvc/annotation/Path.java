package com.telemarket.telemarketer.mvc.annotation;

import java.lang.annotation.*;

/**
 * 标注路径
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Path {
    String value() default "/";
}