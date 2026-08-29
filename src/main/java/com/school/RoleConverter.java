package com.school;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

@Converter(autoApply = false)
public class RoleConverter implements AttributeConverter<Role, Object> {
  @Override
  public Object convertToDatabaseColumn(Role attribute) {
    if (attribute == null) return null;
    PGobject pg = new PGobject();
    try {
      pg.setType("user_role");
      pg.setValue(attribute.name());
      return pg;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Role convertToEntityAttribute(Object dbData) {
    if (dbData == null) return null;
    String val;
    if (dbData instanceof PGobject) {
      val = ((PGobject) dbData).getValue();
    } else {
      val = dbData.toString();
    }
    return Role.valueOf(val);
  }
}
