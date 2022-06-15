package com.czertainly.core.util;

import com.czertainly.api.model.common.attribute.AttributeCallback;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.AttributeType;
import com.czertainly.api.model.common.attribute.content.BaseAttributeContent;
import com.czertainly.api.model.common.attribute.content.FileAttributeContent;
import com.czertainly.api.model.common.attribute.content.JsonAttributeContent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AttributeMigrationUtils {
    private static final Logger logger = LoggerFactory.getLogger(AttributeMigrationUtils.class);

    public static List<String> getMigrationCommands(ResultSet rows, String tableName, String columnName) throws SQLException, JsonProcessingException {
        logger.debug("Migrating Table: {}, Column: {}", tableName, columnName);
        ObjectMapper mapper = new ObjectMapper();
        List<String> migrationCommands = new ArrayList<>();
        while (rows.next()) {
            // the certificate_location table has composite key
            if (tableName.equals("certificate_location")) {
                logger.debug("Migrating record with Location Id: {}, Certificate Id: {}", rows.getString("location_id"), rows.getString("certificate_id"));
            } else {
                logger.debug("Migrating record with Id: {}", rows.getString("id"));
            }
            List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
            List<Map<String, Object>> oldAttributeValue = mapper.readValue(rows.getString(columnName), new TypeReference<>() {
            });
            for (Map<String, Object> item : oldAttributeValue) {
                attributeDefinitions.add(getNewAttributes(item));
            }
            String updateCommand;
            String serializedAttributes = AttributeDefinitionUtils.serialize(attributeDefinitions);
            if (tableName.equals("certificate_location")) {
                updateCommand = "UPDATE " + tableName + " SET " + columnName + " = '" + serializedAttributes + "' WHERE location_id = " + rows.getString("location_id") + " AND certificate_id = " + rows.getString("certificate_id") + ";";
            } else {
                updateCommand = "UPDATE " + tableName + " SET " + columnName + " = '" + serializedAttributes + "' WHERE id = " + rows.getString("id") + ";";
            }
            migrationCommands.add(updateCommand);
        }
        return migrationCommands;
    }

    public static AttributeDefinition getNewAttributes(Map<String, Object> oldAttribute) {
        AttributeDefinition attributeDefinition = new AttributeDefinition();
        attributeDefinition.setLabel(((String) oldAttribute.get("label")).replaceAll("'", "''"));
        attributeDefinition.setName(((String) oldAttribute.get("name")).replaceAll("'", "''"));
        attributeDefinition.setUuid((String) oldAttribute.get("uuid"));
        attributeDefinition.setReadOnly((Boolean) oldAttribute.get("readOnly"));
        if (oldAttribute.get("required") != null) {
            attributeDefinition.setRequired((Boolean) oldAttribute.get("required"));
        }
        if (oldAttribute.get("description") != null) {
            attributeDefinition.setDescription(((String) oldAttribute.get("description")).replaceAll("'", "''"));
        }
        if (oldAttribute.get("visible") != null) {
            attributeDefinition.setVisible((Boolean) oldAttribute.get("visible"));
        }
        if (oldAttribute.get("multiValue") != null) {
            attributeDefinition.setList((Boolean) oldAttribute.get("multiValue"));
        }
        if (oldAttribute.get("validationRegex") != null) {
            attributeDefinition.setValidationRegex(((String) oldAttribute.get("validationRegex")).replaceAll("'", "''"));
        }
        if (oldAttribute.get("value") != null) {
            attributeDefinition.setMultiSelect(isMultiselect(oldAttribute.get("value")));
            attributeDefinition.setContent(getAttributeValue(oldAttribute.get("value"), (String) oldAttribute.get("type")));
        }
        attributeDefinition.setType(getAttributeType(oldAttribute.get("value"), (String) oldAttribute.get("type")));
        attributeDefinition.setList(isAttributeList((String) oldAttribute.get("type")));
        if (oldAttribute.get("attributeCallback") != null) {
            ObjectMapper mapper = new ObjectMapper();
            String callbackAsString;
            try {
                callbackAsString = mapper.writeValueAsString(oldAttribute.get("attributeCallback"));
                AttributeCallback attributeCallback = mapper.readValue(callbackAsString, AttributeCallback.class);
                attributeDefinition.setAttributeCallback(attributeCallback);
            } catch (JsonProcessingException e) {
                logger.error("Unable to process callback data for attribute {}", oldAttribute.get("name"));
                throw new RuntimeException("Unable to process callback data for attribute " + oldAttribute.get("name"));
            }
        }
        return attributeDefinition;
    }

    private static AttributeType getAttributeType(Object oldValue, String oldAttributeType) {
        if (oldAttributeType.equals("LIST")) {
            if (oldValue instanceof List) {
                if (((List<?>) oldValue).get(0) instanceof String) {
                    return AttributeType.STRING;
                } else {
                    return AttributeType.JSON;
                }
            } else if (oldValue instanceof Map) {
                return AttributeType.JSON;
            } else if (oldValue instanceof String) {
                return AttributeType.STRING;
            } else if (oldValue instanceof Integer) {
                return AttributeType.INTEGER;
            } else if (oldValue instanceof Float) {
                return AttributeType.FLOAT;
            }
        }
        if (oldValue instanceof Integer) {
            return AttributeType.INTEGER;
        }
        if (oldValue instanceof Float) {
            return AttributeType.FLOAT;
        }

        return AttributeType.fromCode((oldAttributeType).toLowerCase());
    }

    private static Boolean isMultiselect(Object oldValue) {
        return oldValue instanceof List;
    }

    private static Boolean isAttributeList(String oldAttributeType) {
        return oldAttributeType.equals("LIST");
    }

    private static Object getAttributeValue(Object oldValue, String oldType) {
        if (oldType.equals("FILE")) {
            return new FileAttributeContent() {{
                setValue((String) oldValue);
            }};
        }
        if (oldValue instanceof String) {
            return new BaseAttributeContent<String>() {{
                setValue((String) oldValue);
            }};
        } else if (oldValue instanceof Float) {
            return new BaseAttributeContent<Float>() {{
                setValue((Float) oldValue);
            }};
        } else if (oldValue instanceof Boolean) {
            return new BaseAttributeContent<Boolean>() {{
                setValue((Boolean) oldValue);
            }};
        } else if (oldValue instanceof Integer) {
            return new BaseAttributeContent<Integer>() {{
                setValue((Integer) oldValue);
            }};
        } else if (oldValue instanceof List || oldType.equals("LIST") || oldType.equals("CREDENTIAL")) {
            if (oldValue instanceof List) {
                if (((List<?>) oldValue).get(0) instanceof String) {
                    List<BaseAttributeContent<String>> multiObject = new ArrayList<>();
                    for (String innerValue : ((List<String>) oldValue)) {
                        multiObject.add(new BaseAttributeContent<>() {{
                            setValue(innerValue);
                        }});
                    }
                    return multiObject;
                } else if (((List<?>) oldValue).get(0) instanceof Map) {
                    List<JsonAttributeContent> multiObject = new ArrayList<>();
                    for (Map<String, Object> insValue : (List<Map<String, Object>>) oldValue) {
                        String primKey;
                        if (insValue.containsKey("name")) {
                            primKey = (String) insValue.get("name");
                        } else {
                            try {
                                primKey = (String) insValue.get(insValue.keySet().stream().findFirst().orElse("error"));
                            } catch (Exception e) {
                                primKey = "error";
                            }
                        }
                        String finalPrimKey = primKey;
                        multiObject.add(new JsonAttributeContent() {{
                            setValue(finalPrimKey);
                            setData(insValue);
                        }});
                    }
                    return multiObject;
                }
            } else {
                String primKey;
                Map<String, Object> insValue = (Map<String, Object>) oldValue;
                if (insValue.containsKey("name")) {
                    primKey = (String) insValue.get("name");
                } else {
                    try {
                        primKey = (String) insValue.get(insValue.keySet().stream().findFirst().orElse("error"));
                    } catch (Exception e) {
                        primKey = "error";
                    }
                }
                String finalPrimKey = primKey;
                return new JsonAttributeContent() {{
                    setValue(finalPrimKey);
                    setData(oldValue);
                }};
            }
        }
        throw new RuntimeException("Unsupported attribute type: " + oldType);
    }
}