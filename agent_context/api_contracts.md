# API Contracts

## Endpoints

### GET /api/resource
Returns a list of resources.

**Response:**
```json
{
  "data": [],
  "pagination": {}
}
```

### POST /api/resource
Creates a new resource.

**Request:**
```json
{
  "name": "string",
  "type": "string"
}
```
