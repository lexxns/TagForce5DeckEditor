package com.lexxns.tagforcedeckeditor.query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteWrapper {
    private final String connectionString;
    private Connection connection;
    private final List<Statement> openStatements;
    private final List<ResultSet> openResultSets;

    public SQLiteWrapper(String dbFilePath) {
        connectionString = "jdbc:sqlite:" + dbFilePath;
        openStatements = new ArrayList<>();
        openResultSets = new ArrayList<>();
    }

    public int insert(Query query) throws SQLException {
        Statement statement = connection.createStatement();
        statement.executeUpdate(query.getSql());
        int id = getLastId(statement);
        statement.close();
        return id;
    }

    private int getLastId(Statement statement) throws SQLException {
        int id = 0;
        ResultSet results = statement.getGeneratedKeys();
        if (results.next())
            id = results.getInt(1);
        results.close();
        return id;
    }

    public ResultSet select(Query query) throws SQLException {
        Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        ResultSet results = statement.executeQuery(query.getSql());
        this.cache(statement, results);
        return results;
    }

    public void update(Query query) throws SQLException {
        Statement statement = connection.createStatement();
        int count = statement.executeUpdate(query.getSql());
        if (count == 0) {
            throw new SQLException("Nothing has been updated.");
        }
        statement.close();
    }

    public void delete(Query query) throws SQLException {
        Statement statement = connection.createStatement();
        int count = statement.executeUpdate(query.getSql());
        if (count == 0) {
            throw new SQLException("Nothing has been deleted.");
        }
        statement.close();
    }

    public void open() throws SQLException {
        connection = DriverManager.getConnection(connectionString);
    }

    private void cache(Statement statement, ResultSet results) {
        openStatements.add(statement);
        openResultSets.add(results);
    }

    public void close() throws SQLException {
        closeResultSets();
        closeStatements();
        connection.close();
    }

    private void closeResultSets() throws SQLException {
        for (ResultSet results : openResultSets) {
            results.close();
        }
        openResultSets.clear();
    }

    private void closeStatements() throws SQLException {
        for (Statement statement : openStatements) {
            statement.close();
        }
        openStatements.clear();
    }
}