package com.cba.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts PII fields.
 * Usage in entity: @Convert(converter = EncryptedStringConverter.class)
 *
 * autoApply = false: explicit opt-in per field keeps non-PII columns unencrypted.
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Autowired
    private FieldEncryptor fieldEncryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return fieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return fieldEncryptor.decrypt(dbData);
    }
}
