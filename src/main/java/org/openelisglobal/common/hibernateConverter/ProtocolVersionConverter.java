package org.openelisglobal.common.hibernateConverter;

import jakarta.persistence.AttributeConverter;
import org.openelisglobal.analyzer.valueholder.ProtocolVersion;
import org.openelisglobal.common.log.LogEvent;

/**
 * Converts legacy protocol_version DB values (e.g. "1.0") to enum values.
 */
public class ProtocolVersionConverter implements AttributeConverter<ProtocolVersion, String> {

    @Override
    public String convertToDatabaseColumn(ProtocolVersion protocolVersion) {
        return protocolVersion == null ? null : protocolVersion.name();
    }

    @Override
    public ProtocolVersion convertToEntityAttribute(String dbValue) {
        if (dbValue == null || dbValue.trim().isEmpty()) {
            return ProtocolVersion.ASTM_LIS2_A2;
        }

        String normalized = dbValue.trim();

        try {
            return ProtocolVersion.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            ProtocolVersion resolved = ProtocolVersion.fromValue(normalized);
            if (resolved != null) {
                return resolved;
            }
            LogEvent.logWarn(getClass().getSimpleName(), "convertToEntityAttribute",
                    "Unknown protocol_version value '" + dbValue + "', defaulting to ASTM_LIS2_A2");
            return ProtocolVersion.ASTM_LIS2_A2;
        }
    }
}
