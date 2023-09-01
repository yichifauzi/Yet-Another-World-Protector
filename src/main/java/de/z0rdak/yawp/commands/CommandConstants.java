package de.z0rdak.yawp.commands;

public enum CommandConstants {
    ACTIVATE("activate"),
    ADD("add"),
    ADD_FLAG("add-flag"),
    ADD_OFFLINE("add-offline"),
    ADD_PLAYER("add-player"),
    ADD_TEAM("add-team"),
    AFFILIATION("affiliation"),
    ALERT("alert"),
    MUTE("mute"),
    ALL("all"),
    ALLOW("allow"),
    AREA("area"),
    BLOCKS("blocks"),
    CHILD("child"),
    CHILDREN("children"),
    CLEAR("clear"),
    CREATE("create"),
    DEACTIVATE("deactivate"),
    DEC("-"),
    DEFAULT_Y("y-default"),
    DELETE("delete"),
    DENY("deny"),
    DIM("dim"),
    SRC_DIM("src-dim"),
    LOCAL("local"),
    GLOBAL("global"),
    ENABLE("enable"),
    OVERRIDE("override"),
    EXPAND("expand"),
    FLAG("flag"),
    FLAGS("flags"),
    FLAG_TYPE("flag-type"),
    FALSE("false"),
    TRUE("true"),
    HELP("help"),
    INC("+"),
    INFO("info"),
    COPY("copy"),
    LIST("list"),
    BOOL("bool"),
    INT("int"),
    MEMBER("member"),
    NAME("name"),
    MARKER("marker"),
    GIVE("give"),
    OWNER("owner"),
    PARENT("parent"),
    PLAYER("player"),
    PLAYERS("players"),
    PRIORITY("priority"),
    REGION("region"),
    SRC_REGION("src-region"),
    PAGE("page"),
    REGIONS("regions"),
    REMOVE("remove"),
    REMOVE_ALL("remove-all"),
    REMOVE_FLAG("remove-flag"),
    REMOVE_OFFLINE("remove-offline"),
    REMOVE_PLAYER("remove-player"),
    REMOVE_TEAM("remove-team"),
    RESET("reset"),
    SELECT("select"),
    SET("set"),
    SPATIAL("spatial"),
    STATE("state"),
    MSG("msg"),
    VALUE("value"),
    TARGET("target"),
    TEAM("team"),
    TEAMS("teams"),
    TELEPORT("tp"),
    TEMPLATE("template"),
    TRIGGER("trigger"),
    TYPE("type"),
    UPDATE("update"),
    VERT("vert"),
    Y1("Y1"),
    Y2("Y2");

    private final String cmdString;

    CommandConstants(final String cmdString) {
        this.cmdString = cmdString;
    }

    @Override
    public String toString() {
        return cmdString;
    }
}
