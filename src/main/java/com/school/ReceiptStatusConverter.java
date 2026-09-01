package com.school;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.SQLException;
import org.postgresql.util.PGobject;

@Converter(autoApply = false)
public class ReceiptStatusConverter implements AttributeConverter<ReceiptStatus, Object> {
  @Override
  public Object convertToDatabaseColumn(ReceiptStatus attribute) {
    if (attribute == null) return null;
    PGobject pg = new PGobject();
    try {
      pg.setType("receipt_status");
      pg.setValue(attribute.name());
      return pg;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ReceiptStatus convertToEntityAttribute(Object dbData) {
    if (dbData == null) return null;
    String val;
    if (dbData instanceof PGobject) {
      val = ((PGobject) dbData).getValue();
    } else {
      val = dbData.toString();
    }
    return ReceiptStatus.valueOf(val);
  }
}
