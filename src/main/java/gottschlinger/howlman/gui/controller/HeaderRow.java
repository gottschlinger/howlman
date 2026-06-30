package gottschlinger.howlman.gui.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class HeaderRow {
    private final SimpleStringProperty key;
    private final SimpleStringProperty value;
    private final SimpleBooleanProperty enabled;

    public HeaderRow(String key, String value) {
        this(key, value, true);
    }

    public HeaderRow(String key, String value, boolean enabled) {
        this.key     = new SimpleStringProperty(key);
        this.value   = new SimpleStringProperty(value);
        this.enabled = new SimpleBooleanProperty(enabled);
    }

    public StringProperty keyProperty()   { return key; }
    public StringProperty valueProperty() { return value; }
    public BooleanProperty enabledProperty() { return enabled; }
    public String getKey()   { return key.get(); }
    public String getValue() { return value.get(); }
    public boolean isEnabled() { return enabled.get(); }
    public void setKey(String v)   { key.set(v); }
    public void setValue(String v) { value.set(v); }
    public void setEnabled(boolean v) { enabled.set(v); }
}
