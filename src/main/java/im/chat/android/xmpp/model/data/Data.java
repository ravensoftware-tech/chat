package im.conversations.android.xmpp.model.data;

import android.util.Log;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import tech.ravensoftware.chat.Config;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@XmlElement(name = "x")
public class Data extends Extension {

    private static final String FORM_TYPE = "FORM_TYPE";
    private static final String FIELD_TYPE_HIDDEN = "hidden";
    private static final String FORM_TYPE_SUBMIT = "submit";

    public Data() {
        super(Data.class);
    }

    public String getFormType() {
        final var fields = this.getExtensions(Field.class);
        final var formTypeField =
                Iterables.tryFind(fields, f -> FORM_TYPE.equals(f.getFieldName()));
        if (formTypeField.isPresent()) {
            return formTypeField.get().getValue();
        } else {
            return null;
        }
    }

    public Collection<Field> getFields() {
        return Collections2.filter(
                this.getExtensions(Field.class), f -> !FORM_TYPE.equals(f.getFieldName()));
    }

    public Field getFieldByName(final String name) {
        return Iterables.find(getFields(), f -> name.equals(f.getFieldName()), null);
    }

    public String getValue(final String name) {
        final var field = getFieldByName(name);
        return field == null ? null : field.getValue();
    }

    private void addField(final String name, final Object value) {
        addField(name, value, null);
    }

    private void addField(final String name, final Object value, final String type) {
        if (value == null) {
            throw new IllegalArgumentException("Null values are not supported on data fields");
        }
        final var field = this.addExtension(new Field());
        field.setFieldName(name);
        if (type != null) {
            field.setType(type);
        }
        if (value instanceof Collection<?> collection) {
            Log.d(Config.LOGTAG, "submitting collection: " + collection);
            for (final Object subValue : collection) {
                if (subValue == null) {
                    Log.d(Config.LOGTAG, "null value in the values for " + name);
                } else if (subValue instanceof String s) {
                    final var valueExtension = field.addExtension(new Value());
                    valueExtension.setContent(s);
                } else {
                    throw new IllegalArgumentException(
                            String.format(
                                    "%s is not a supported field value",
                                    subValue.getClass().getSimpleName()));
                }
            }
        } else {
            final var valueExtension = field.addExtension(new Value());
            if (value instanceof String s) {
                valueExtension.setContent(s);
            } else if (value instanceof Enum<?> e) {
                valueExtension.setContent(
                        CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, e.toString()));
            } else if (value instanceof Integer i) {
                valueExtension.setContent(String.valueOf(i));
            } else if (value instanceof Boolean b) {
                valueExtension.setContent(Boolean.TRUE.equals(b) ? "1" : "0");
            } else {
                throw new IllegalArgumentException(
                        String.format(
                                "%s is not a supported field value",
                                value.getClass().getSimpleName()));
            }
        }
    }

    private void setFormType(final String formType) {
        this.addField(FORM_TYPE, formType, FIELD_TYPE_HIDDEN);
    }

    public static Data of(final Map<String, Object> values, @Nullable final String formType) {
        final var data = new Data();
        data.setType(FORM_TYPE_SUBMIT);
        if (formType != null) {
            data.setFormType(formType);
        }
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            data.addField(entry.getKey(), entry.getValue());
        }
        return data;
    }

    public Data submit(final Map<String, Object> values) {
        final String formType = this.getFormType();
        final var submit = new Data();
        submit.setType(FORM_TYPE_SUBMIT);
        if (formType != null) {
            submit.setFormType(formType);
        }
        for (final Field existingField : this.getFields()) {
            final var fieldName = existingField.getFieldName();
            final Object submittedValue = values.get(fieldName);
            if (fieldName == null && Field.Type.FIXED.equals(existingField.getType())) {
                continue;
            }
            if (submittedValue != null) {
                submit.addField(fieldName, submittedValue);
            } else {
                submit.addExtension(existingField);
            }
        }
        return submit;
    }

    private void setType(final String type) {
        this.setAttribute("type", type);
    }

    public Map<String, Object> asMap() {
        final var builder = new ImmutableMap.Builder<String, Object>();
        for (final var field : getFields()) {
            final var name = field.getFieldName();
            if (name == null) {
                continue;
            }
            final var values = field.getValues();
            if (values.size() == 1) {
                builder.put(name, Iterables.getOnlyElement(values));
            } else {
                builder.put(name, values);
            }
        }
        return builder.build();
    }
}
