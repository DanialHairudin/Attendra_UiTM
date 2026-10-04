package com.attendra.uitm.data;

/**
 * Result of an async Firebase call. Repositories call exactly one of these.
 * The error is already a short message that can be shown to the user.
 */
public interface Callback<T> {
    void onSuccess(T result);

    void onError(String message);
}
