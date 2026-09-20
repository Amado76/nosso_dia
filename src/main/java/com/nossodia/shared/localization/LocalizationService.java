package com.nossodia.shared.localization;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class LocalizationService {
    private final MessageSource messages;

    public LocalizationService(MessageSource messages) {
        this.messages = messages;
    }

    public String get(String key, Object... arguments) {
        return messages.getMessage(key, arguments, LocaleContextHolder.getLocale());
    }
}
