package com.lexxns.tagforcedeckeditor.query;

import java.util.ArrayList;
import java.util.List;

public class SelectQuery extends SearchQuery {
    private final String table;
    private final List<String> fields;

    public SelectQuery(String table) {
        this.table = table;
        this.fields = new ArrayList<>();
        this.sql = String.format("SELECT * FROM %s", table);
    }

    public SelectQuery field(String field) {
        fields.add(field);
        this.sql = String.format("SELECT %s FROM %s", String.join(", ", fields), table);
        return this;
    }

    public SelectQuery orderBy(String field, String direction) {
        this.add(String.format("ORDER BY %s %s", field, direction));
        return this;
    }

    public SelectQuery limit(int limit) {
        this.add(String.format("LIMIT %o", limit));
        return this;
    }

    public SelectQuery offset(int offset) {
        this.add(String.format("OFFSET %o", offset));
        return this;
    }
}
