package com.lexxns.tagforcedeckeditor.query;

public class SearchQuery extends Query {
    public SearchQuery where(String clause) {
        this.add(String.format("WHERE %s", clause));
        return this;
    }

    public SearchQuery andWhere(String clause) {
        this.add(String.format("AND %s", clause));
        return this;
    }

    public SearchQuery orWhere(String clause) {
        this.add(String.format("OR %s", clause));
        return this;
    }
}