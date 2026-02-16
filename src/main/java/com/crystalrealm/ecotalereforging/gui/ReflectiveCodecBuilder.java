package com.crystalrealm.ecotalereforging.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds BuilderCodec instances entirely through reflection to avoid
 * NoSuchMethodError / NoSuchFieldError caused by stub descriptor mismatches.
 */
@SuppressWarnings("unchecked")
final class ReflectiveCodecBuilder<T> {

    private static final Class<?> CODEC_CLASS;
    private static final Class<?> KEYED_CODEC_CLASS;
    private static final Class<?> BUILDER_CODEC_CLASS;
    private static final Object   CODEC_STRING;

    static {
        try {
            CODEC_CLASS = Class.forName("com.hypixel.hytale.codec.Codec");
            KEYED_CODEC_CLASS = Class.forName("com.hypixel.hytale.codec.KeyedCodec");
            BUILDER_CODEC_CLASS = Class.forName("com.hypixel.hytale.codec.builder.BuilderCodec");

            Field stringField = CODEC_CLASS.getField("STRING");
            CODEC_STRING = stringField.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Object builder;

    private ReflectiveCodecBuilder(Object builder) {
        this.builder = builder;
    }

    static <T> ReflectiveCodecBuilder<T> create(Class<T> dataClass, Supplier<T> constructor) {
        try {
            Method builderMethod = BUILDER_CODEC_CLASS.getMethod("builder", Class.class, Supplier.class);
            Object b = builderMethod.invoke(null, dataClass, constructor);
            return new ReflectiveCodecBuilder<>(b);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create BuilderCodec.builder()", e);
        }
    }

    private static Object newKeyedCodec(String key) {
        try {
            var ctor = KEYED_CODEC_CLASS.getConstructor(String.class, CODEC_CLASS);
            return ctor.newInstance(key, CODEC_STRING);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create KeyedCodec(\"" + key + "\", Codec.STRING)", e);
        }
    }

    ReflectiveCodecBuilder<T> addStringField(String key,
                                              BiConsumer<T, String> setter,
                                              Function<T, String> getter) {
        try {
            Object keyedCodec = newKeyedCodec(key);
            Method addFieldMethod = findMethod(builder.getClass(), "addField", 3);
            builder = addFieldMethod.invoke(builder, keyedCodec, setter, getter);
            return this;
        } catch (Exception e) {
            throw new RuntimeException("Cannot call addField(\"" + key + "\")", e);
        }
    }

    <R> R build() {
        try {
            Method buildMethod = builder.getClass().getMethod("build");
            return (R) buildMethod.invoke(builder);
        } catch (Exception e) {
            throw new RuntimeException("Cannot call build()", e);
        }
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        throw new RuntimeException("Method " + name + " with " + paramCount
                + " params not found on " + clazz.getName());
    }
}
