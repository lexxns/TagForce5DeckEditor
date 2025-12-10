package com.lexxns.tagforcedeckeditor.query;

public class Query {
    protected String sql;

    public Query() {
        sql = "";
    }

    public void add(String sql) {
        this.sql += " " + sql;
    }

    protected void setSql(String sql) {
        this.sql = sql;
    }

    public void clear() {
        this.sql = "";
    }

    public String getSql() {
        return this.sql + ";";
    }
}