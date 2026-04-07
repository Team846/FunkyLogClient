package com.funkylogclient;

public class Styles {
    
    public static final String CENTER = "";
    
    public static final String BG_DARKEST = "#1A1A1A";
    public static final String BG_DARK = "#222222";
    public static final String BG_MEDIUM = "#2A2A2A";
    public static final String BG_LIGHT = "#333333";
    public static final String BG_LIGHTER = "#3D3D3D";
    public static final String BG_HOVER = "#404040";
    public static final String BG_HOVER_LIGHT = "#4A4A4A";
    public static final String BG_ACTIVE = "#505050";
    
    public static final String BORDER_DARK = "#333333";
    public static final String BORDER_MEDIUM = "#404040";
    public static final String BORDER_LIGHT = "#4A4A4A";
    
    public static final String TEXT_PRIMARY = "#E0E0E0";
    public static final String TEXT_SECONDARY = "#B0B0B0";
    public static final String TEXT_MUTED = "#808080";
    public static final String TEXT_WHITE = "#FFFFFF";
    public static final String TEXT_BLUE = "#4400ffff";
    public static final String TEXT_RED = "#ff0000ff";
    
    public static final String ACCENT_PRIMARY = "#FF8C00";
    public static final String ACCENT_HOVER = "#FFA333";
    public static final String ACCENT_YELLOW = "#FFD700";
    public static final String ACCENT_ERROR = "#F85149";
    public static final String ACCENT_WARNING = "#FFB347";
    
    public static final String FONT_FAMILY = "'Segoe UI', 'Roboto', sans-serif";
    
    public static String bg(String color) { return "-fx-background-color: " + color + ";"; }
    public static String fill(String color) { return "-fx-fill: " + color + ";"; }
    public static String textFill(String color) { return "-fx-text-fill: " + color + ";"; }
    public static String border(String color) { return "-fx-border-color: " + color + ";"; }

    public static final String LOGO_STYLE = "-fx-border-width: 5px;";

    public static final String TEXT_STYLE = "-fx-font-size: 20px; -fx-fill: " + TEXT_PRIMARY + "; -fx-font-family: " + FONT_FAMILY + ";";
    public static final String TEXT_MED = "-fx-font-size: 14px; -fx-fill: " + TEXT_PRIMARY + "; -fx-font-family: " + FONT_FAMILY + ";";
    public static final String TEXT_GMED = "-fx-font-size: 16px; -fx-fill: " + TEXT_PRIMARY + "; -fx-font-family: " + FONT_FAMILY + ";";
    public static final String TEXT_SMALL = "-fx-font-size: 14px; -fx-fill: " + TEXT_PRIMARY + "; -fx-font-family: " + FONT_FAMILY + ";";
    public static final String TEXT_SMALLER = "-fx-font-size: 11px; -fx-fill: " + TEXT_PRIMARY + "; -fx-font-family: " + FONT_FAMILY + ";";
    public static final String BOLD_TEXT = " -fx-font-weight: bold;";

    public static final String DEFAULT_MSG = "-fx-border-width: 3px; -fx-border-radius: 10px; -fx-background-radius: 10px;";

    public static final String SCROLL_PANE_STYLE = "-fx-background-color: " + BG_DARKEST + "; -fx-border-radius: 8px;";

    public static final String RIGHT_SIDEBAR_STYLE = "-fx-background-radius: 0 8 8 0; -fx-background-color: " + BG_DARK + ";"
            + "-fx-border-color: transparent transparent transparent " + BORDER_DARK + "; -fx-border-width: 1px;";

    public static final String BUTTON_STYLE = "-fx-background-color: " + BG_LIGHTER + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 10 20 10 20; -fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + "; -fx-cursor: hand;";
    public static final String BUTTON_HOVER_STYLE = "-fx-background-color: " + BG_ACTIVE + "; -fx-text-fill: " + TEXT_WHITE + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 10 20 10 20; -fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + "; -fx-cursor: hand;";
    public static final String BUTTON_PRESSED_STYLE = "-fx-background-color: " + ACCENT_PRIMARY + "; -fx-text-fill: " + TEXT_WHITE + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 10 20 10 20; -fx-font-size: 13px; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + "; -fx-cursor: hand;";

    public static final String SMALL_BUTTON_STYLE = "-fx-background-color: " + BG_LIGHTER + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6 12 6 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + "; -fx-cursor: hand;";
    public static final String SMALL_BUTTON_HOVER_STYLE = "-fx-background-color: " + BG_ACTIVE + "; -fx-text-fill: " + TEXT_WHITE + "; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 6 12 6 12; -fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + "; -fx-cursor: hand;";

    public static final String ACCENT_BUTTON_STYLE = "-fx-background-color: " + ACCENT_PRIMARY + "; -fx-text-fill: " + TEXT_WHITE + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 8 16 8 16; -fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + "; -fx-cursor: hand;";
    public static final String ACCENT_BUTTON_HOVER_STYLE = "-fx-background-color: " + ACCENT_HOVER + "; -fx-text-fill: " + TEXT_WHITE + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 8 16 8 16; -fx-font-size: 12px; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + "; -fx-cursor: hand;";

    public static final String CONFIRM_BUTTON_STYLE = "-fx-font-size: 14px; -fx-background-color: " + ACCENT_PRIMARY + "; -fx-text-fill: white; -fx-padding: 6px 10px; -fx-background-radius: 6px; -fx-cursor: hand;";
    public static final String CONFIRM_BUTTON_HOVER_STYLE = "-fx-font-size: 14px; -fx-background-color: " + ACCENT_HOVER + "; -fx-text-fill: white; -fx-padding: 6px 10px; -fx-background-radius: 6px; -fx-cursor: hand;";

    public static final String TEXT_FIELD_STYLE = "-fx-background-color: " + BG_MEDIUM + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-border-color: " + BORDER_MEDIUM + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 10 14 10 14; -fx-font-size: 13px; -fx-font-family: " + FONT_FAMILY + ";";
    public static final String TEXT_FIELD_FOCUSED_STYLE = "-fx-background-color: " + BG_MEDIUM + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-border-color: " + ACCENT_PRIMARY + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 10 14 10 14; -fx-font-size: 13px; -fx-font-family: " + FONT_FAMILY + ";";

    public static final String WIDGET_TEXT_FIELD_STYLE = "-fx-background-color: " + BG_MEDIUM + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-border-color: " + BORDER_DARK + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 8px 12px; -fx-font-size: 16px; -fx-font-family: " + FONT_FAMILY + ";";
    public static final String WIDGET_TEXT_FIELD_FOCUSED_STYLE = "-fx-background-color: " + BG_MEDIUM + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-border-color: " + ACCENT_PRIMARY + "; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 8px 12px; -fx-font-size: 16px; -fx-font-family: " + FONT_FAMILY + ";";

    public static final String CHECKBOX_STYLE = "-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 14px; -fx-font-family: " + FONT_FAMILY + ";";

    public static final String LABEL_MED = "-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 14px; -fx-font-family: " + FONT_FAMILY + ";";
    public static final String LABEL_TITLE = "-fx-font-size: 18px; -fx-fill: " + TEXT_WHITE + "; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + ";";

    public static final String SEARCH_BAR_STYLE = "-fx-background-color: " + BG_LIGHT + "; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-border-color: " + BORDER_MEDIUM + "; -fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-padding: 10 18 10 18; -fx-font-size: 14px; -fx-font-family: " + FONT_FAMILY + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 1);";
    public static final String SEARCH_BAR_FOCUSED_STYLE = "-fx-background-color: " + BG_LIGHT + "; -fx-text-fill: " + TEXT_WHITE + "; -fx-border-color: " + ACCENT_PRIMARY + "; -fx-border-width: 2px; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-padding: 9 17 9 17; -fx-font-size: 14px; -fx-font-family: " + FONT_FAMILY + "; -fx-effect: dropshadow(gaussian, rgba(255,140,0,0.3), 6, 0, 0, 2);";

    public static final String SEARCH_CONTAINER_STYLE = "-fx-background-color: transparent; -fx-background-radius: 0; -fx-padding: 12 16 12 16; -fx-border-color: transparent; -fx-border-width: 0;";

    public static final String SECTION_HEADER_STYLE = "-fx-font-size: 16px; -fx-fill: " + TEXT_WHITE + "; -fx-font-weight: bold; -fx-font-family: " + FONT_FAMILY + ";";

    public static final String SECTION_DIVIDER_STYLE = "-fx-border-width: 0 0 1px 0; -fx-border-color: " + BORDER_DARK + ";";

    public static final String WIDGET_CONTAINER_STYLE = "-fx-background-color: " + BG_DARK + "; -fx-background-radius: 10px; -fx-border-radius: 10px; -fx-border-color: " + BORDER_DARK + "; -fx-border-width: 1px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);";
    public static final String WIDGET_TITLE_STYLE = "-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px; -fx-font-family: " + FONT_FAMILY + ";";

    public static final String COMBO_BOX_STYLE = "-fx-background-color: " + BG_LIGHT + "; -fx-background-radius: 6px;";

    public static final String TREE_CELL_VALUE_STYLE = "-fx-padding: 4px 8px; -fx-font-size: 12px; -fx-font-family: " + FONT_FAMILY + "; -fx-text-fill: " + ACCENT_PRIMARY + "; -fx-font-weight: normal;";
    public static final String TREE_CELL_FIELD_STYLE = "-fx-padding: 4px 8px; -fx-font-size: 12px; -fx-font-family: " + FONT_FAMILY + "; -fx-text-fill: " + ACCENT_YELLOW + "; -fx-font-weight: bold;";
    public static final String TREE_CELL_DEFAULT_STYLE = "-fx-padding: 4px 8px; -fx-font-size: 12px; -fx-font-family: " + FONT_FAMILY + "; -fx-text-fill: " + TEXT_PRIMARY + ";";

    public static final String STATUS_DOT_CONNECTED = "-fx-effect: dropshadow(gaussian, rgba(255,215,0,0.4), 6, 0, 0, 0);";
    public static final String STATUS_DOT_DISCONNECTED = "-fx-effect: dropshadow(gaussian, rgba(248,81,73,0.4), 6, 0, 0, 0);";
}
