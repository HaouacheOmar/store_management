package com.store.common.web;

import java.util.Map;

/** Format d'erreur commun à toutes les API : {code, message, details}. */
public record ApiError(String code, String message, Map<String, Object> details) {
}
