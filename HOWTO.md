# jpost — How-To Guide

A lightweight command-line HTTP client. Store named requests, switch environments,
and run requests by name — like Postman but from the terminal.

---

## Running jpost

```
java -jar jpost-1.0.0.jar <command> [options]
```

All examples below assume you are in the directory containing `jpost-1.0.0.jar`.
Replace `jpost-1.0.0.jar` with the full path if needed.

---

## Quick Start

```
# Fire a one-off GET request
java -jar jpost-1.0.0.jar run --url https://httpbin.org/get

# Save it for later
java -jar jpost-1.0.0.jar request save --name my-get --url https://httpbin.org/get

# Run it by name
java -jar jpost-1.0.0.jar request run my-get
```

---

## Commands

- [`run`](#run--ad-hoc-requests) — fire a one-off request without saving it
- [`request`](#request--saved-requests) — save, run, list, show, remove named requests
- [`env`](#env--environments) — manage environments and their variables
- [`collection`](#collection--collections) — manage collections of saved requests

---

## `run` — Ad-hoc Requests

Run a request without saving it.

```
java -jar jpost-1.0.0.jar run --url <url> [options]
```

### Options

| Option | Description | Default |
|---|---|---|
| `--url` | URL to request (required) | — |
| `--method`, `-X` | HTTP method: GET POST PUT DELETE PATCH | GET |
| `--header`, `-H` | Header as `Key: Value` (repeatable) | — |
| `--body` | Request body string | — |
| `--body-type` | Body type: `json` `form` `raw` | none |
| `--auth-type` | Auth type: `none` `bearer` `basic` | none |
| `--token` | Bearer token (use with `--auth-type bearer`) | — |
| `--username` | Username (use with `--auth-type basic`) | — |
| `--password` | Password (use with `--auth-type basic`) | — |
| `--env` | Environment name to use for this request only | active env |
| `--curl` | Print the equivalent curl command instead of running | — |
| `--verbose` | Print response headers | — |

All enum options (`--method`, `--body-type`, `--auth-type`) are case-insensitive.

### Examples

**Simple GET:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/get
```

**GET with verbose headers:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/get --verbose
```

**POST with a JSON body:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/post --method post --body "{\"name\":\"alice\",\"age\":30}" --body-type json
```

**POST with a form body:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/post --method post --body "user=alice&pass=secret" --body-type form
```

**Custom headers:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/get --header "Accept: application/json" --header "X-Tenant: acme"
```

**Bearer auth:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/bearer --auth-type bearer --token mytoken123
```

**Basic auth:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/basic-auth/alice/secret --auth-type basic --username alice --password secret
```

**See the curl command instead of running:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/post --method post --body "{\"name\":\"alice\"}" --body-type json --curl
```
Output:
```
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{"name":"alice"}' \
  'https://httpbin.org/post'
```

**Test a specific HTTP status code:**
```
java -jar jpost-1.0.0.jar run --url https://httpbin.org/status/404
java -jar jpost-1.0.0.jar run --url https://httpbin.org/status/500
```

---

## `request` — Saved Requests

Save requests by name and run them later, with full environment variable support.

### `request save`

```
java -jar jpost-1.0.0.jar request save --name <name> --url <url> [options]
```

Accepts all the same options as `run` (except `--curl` and `--verbose`), plus:

| Option | Description | Default |
|---|---|---|
| `--name` | Request name (required) | — |
| `--collection` | Collection to save into | default collection |

If a request with the same name already exists it is overwritten.
If the collection does not exist it is created automatically.

**Examples:**

```
java -jar jpost-1.0.0.jar request save --name get-ip --url https://httpbin.org/ip

java -jar jpost-1.0.0.jar request save --name post-data --method post --url https://httpbin.org/post --body "{\"hello\":\"world\"}" --body-type json --auth-type bearer --token mytoken --collection my-api

java -jar jpost-1.0.0.jar request save --name get-users --url "{{baseUrl}}/api/users" --auth-type bearer --token "{{authToken}}" --collection my-api
```

### `request run`

```
java -jar jpost-1.0.0.jar request run <name> [options]
```

| Option | Description | Default |
|---|---|---|
| `--collection` | Collection to look in | default collection |
| `--env` | Environment to use for this run only | active env |
| `--curl` | Print curl command instead of running | — |
| `--verbose` | Print response headers | — |

**Examples:**

```
java -jar jpost-1.0.0.jar request run get-ip

java -jar jpost-1.0.0.jar request run get-users --collection my-api

java -jar jpost-1.0.0.jar request run get-users --collection my-api --env qa

java -jar jpost-1.0.0.jar request run post-data --curl
```

### `request list`

```
java -jar jpost-1.0.0.jar request list [--collection <name>]
```

Lists all saved requests. Without `--collection` shows requests across all collections.

Output format:
```
[collection-name] request-name         METHOD  url
[my-api]          get-users            GET     {{baseUrl}}/api/users
[my-api]          post-data            POST    https://httpbin.org/post
```

### `request show`

```
java -jar jpost-1.0.0.jar request show <name> [--collection <name>]
```

Pretty-prints the saved request as JSON:

```json
{
  "name" : "get-users",
  "method" : "GET",
  "url" : "{{baseUrl}}/api/users",
  "bodyType" : "NONE",
  "auth" : {
    "type" : "BEARER",
    "token" : "{{authToken}}"
  }
}
```

### `request remove`

```
java -jar jpost-1.0.0.jar request remove <name> [--collection <name>]
```

---

## `env` — Environments

Environments hold named variables that get substituted into `{{variable}}` tokens in URLs, headers, bodies, and auth fields.

### Setup example

```
java -jar jpost-1.0.0.jar env create dev
java -jar jpost-1.0.0.jar env set dev baseUrl http://localhost:8080
java -jar jpost-1.0.0.jar env set dev authToken devtoken123

java -jar jpost-1.0.0.jar env create qa
java -jar jpost-1.0.0.jar env set qa baseUrl https://qa.example.com
java -jar jpost-1.0.0.jar env set qa authToken qatoken456

# Switch the active environment globally
java -jar jpost-1.0.0.jar env use dev
```

Now any request with `{{baseUrl}}` or `{{authToken}}` will use the dev values.

### Commands

| Command | Description |
|---|---|
| `env create <name>` | Create a new empty environment |
| `env use <name>` | Set the active environment (used by all requests unless overridden) |
| `env set <env> <key> <value>` | Set a variable in an environment |
| `env list` | List all environment names |
| `env list <env>` | List all variables in a specific environment |
| `env remove <env> <key>` | Remove a single variable from an environment |
| `env delete <name>` | Delete the entire environment |

### Using environments

**Per-request override** (does not change the active environment):
```
java -jar jpost-1.0.0.jar request run get-users --env qa
java -jar jpost-1.0.0.jar run --url "{{baseUrl}}/health" --env prod
```

**Unresolved variables** produce a warning but do not stop execution:
```
Warning: unresolved variable {{baseUrl}} in url
```

---

## `collection` — Collections

Collections group related saved requests together (e.g. one collection per API or project).

```
java -jar jpost-1.0.0.jar collection create <name>
java -jar jpost-1.0.0.jar collection list
java -jar jpost-1.0.0.jar collection remove <name> [--force]
```

`remove` will refuse to delete a collection that still contains requests unless you pass `--force`.

The default collection is named `default`. You can change it by editing `~/.jpost/config.json`.

---

## Variable Interpolation

Any field that supports `{{variable}}` tokens will have them replaced at runtime using the active environment's variables.

Fields that support interpolation:
- URL
- All header values
- Body
- Bearer token
- Basic auth username and password

**Example:**

```
# Save a request with variables
java -jar jpost-1.0.0.jar request save --name create-user --method post --url "{{baseUrl}}/users" --body "{\"name\":\"alice\"}" --body-type json --auth-type bearer --token "{{authToken}}" --collection my-api

# Set up environments
java -jar jpost-1.0.0.jar env create dev
java -jar jpost-1.0.0.jar env set dev baseUrl http://localhost:8080
java -jar jpost-1.0.0.jar env set dev authToken dev-secret

java -jar jpost-1.0.0.jar env create prod
java -jar jpost-1.0.0.jar env set prod baseUrl https://api.example.com
java -jar jpost-1.0.0.jar env set prod authToken prod-secret

# Run against dev
java -jar jpost-1.0.0.jar env use dev
java -jar jpost-1.0.0.jar request run create-user --collection my-api

# Run against prod without changing the active env
java -jar jpost-1.0.0.jar request run create-user --collection my-api --env prod
```

---

## Response Output

```
HTTP 200 OK
-----------------------------

{
  "origin" : "1.2.3.4"
}
```

With `--verbose`:
```
HTTP 200 OK
-----------------------------
content-type: application/json
content-length: 32
date: Fri, 15 May 2026 12:00:00 GMT
-----------------------------

{
  "origin" : "1.2.3.4"
}
```

- JSON responses are automatically pretty-printed.
- Non-JSON responses are printed as-is.
- 4xx and 5xx responses print to stderr and return exit code 1.

---

## Exit Codes

| Code | Meaning |
|---|---|
| 0 | Success (HTTP 2xx/3xx) |
| 1 | Request/collection/environment not found, or HTTP 4xx/5xx |
| 2 | Network / connection failure |
| 3 | Malformed JSON in a storage file under `~/.jpost/` |

---

## Data Storage

All data lives in `~/.jpost/` and is plain JSON — you can edit the files directly.

```
~/.jpost/
├── config.json          ← active environment and default collection
├── collections/
│   ├── default.json
│   └── my-api.json
└── environments/
    ├── dev.json
    └── qa.json
```
