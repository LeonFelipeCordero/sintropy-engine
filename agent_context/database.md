# Database

## Schema Overview

Describe your database schema and key tables here.

## Common Queries

### Query 1: Description
```sql
SELECT * FROM table WHERE condition;
```

### Query 2: Description
```sql
SELECT a.*, b.name
FROM table_a a
JOIN table_b b ON a.id = b.a_id
WHERE condition;
```

## Stored Procedures

### procedure_name
**Purpose**: Description of what this procedure does

**Parameters**:
- `param1` (TYPE): Description
- `param2` (TYPE): Description

```sql
CREATE PROCEDURE procedure_name(param1 TYPE, param2 TYPE)
BEGIN
    -- Implementation
END;
```

## Indexes

Key indexes and their purpose:
- `idx_table_column`: Speeds up queries on X
- `idx_composite`: Used for Y queries

## Data Migrations

Notes about important migrations or data transformations.
