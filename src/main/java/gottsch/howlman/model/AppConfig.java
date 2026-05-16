package gottsch.howlman.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppConfig {

    private String activeEnvironment;
    private String defaultCollection = "default";
    private String editorFontFamily;
    private int editorFontSize = 12;

    public AppConfig() {}

    public String getActiveEnvironment() { return activeEnvironment; }
    public void setActiveEnvironment(String activeEnvironment) { this.activeEnvironment = activeEnvironment; }

    public String getDefaultCollection() { return defaultCollection; }
    public void setDefaultCollection(String defaultCollection) { this.defaultCollection = defaultCollection; }

    public String getEditorFontFamily() { return editorFontFamily; }
    public void setEditorFontFamily(String editorFontFamily) { this.editorFontFamily = editorFontFamily; }

    public int getEditorFontSize() { return editorFontSize; }
    public void setEditorFontSize(int editorFontSize) { this.editorFontSize = editorFontSize; }
}
