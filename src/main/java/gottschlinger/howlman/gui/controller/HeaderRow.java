package gottschlinger.howlman.gui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class HeaderRow {
    private final SimpleStringProperty key;
    private final SimpleStringProperty value;

    public HeaderRow(String key, String value) {
        this.key   = new SimpleStringProperty(key);
        this.value = new SimpleStringProperty(value);
    }

    public StringProperty keyProperty()   { return key; }
    public StringProperty valueProperty() { return value; }
    public String getKey()   { return key.get(); }
    public String getValue() { return value.get(); }
    public void setKey(String v)   { key.set(v); }
    public void setValue(String v) { value.set(v); }
}
