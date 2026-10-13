package com.attendra.uitm.data;

/** Result of an async Firebase call. */
public interface Callback<T> {
    void onSuccess(T result);

    void onError(String message);
}
