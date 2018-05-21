package com.steevsapps.idledaddy.steam.converter;

import android.support.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import in.dragonbra.javasteam.types.KeyValue;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

public class VdfConverterFactory extends Converter.Factory {
    public static VdfConverterFactory create() {
        return new VdfConverterFactory();
    }

    private VdfConverterFactory() {
        // Private constructor
    }

    @Nullable
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        if (!(type instanceof Class<?>)) {
            return null;
        }
        final Class<?> c = (Class<?>) type;
        if (!KeyValue.class.isAssignableFrom(c)) {
            return null;
        }
        return new VdfConverter();
    }

    private static class VdfConverter implements Converter<ResponseBody, KeyValue> {
        @Override
        public KeyValue convert(ResponseBody value) throws IOException {
            final KeyValue kv = new KeyValue();
            kv.readAsText(new ByteArrayInputStream(value.bytes()));
            return kv;
        }
    }
}
