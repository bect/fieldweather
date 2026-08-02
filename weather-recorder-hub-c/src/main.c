#include "../lib/mongoose/mongoose.h"
#include "../lib/cjson/cJSON.h"
#include "../lib/sqlite/sqlite3.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static sqlite3 *db;

// Initialize database
static void init_db() {
    int rc = sqlite3_open("weather.db", &db);
    if (rc) {
        fprintf(stderr, "Can't open database: %s\n", sqlite3_errmsg(db));
        exit(1);
    }

    const char *sql = 
        "CREATE TABLE IF NOT EXISTS weather_records ("
        "local_id INTEGER PRIMARY KEY,"
        "timestamp TEXT,"
        "condition TEXT,"
        "latitude REAL,"
        "longitude REAL,"
        "location_name TEXT UNIQUE);"
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_records_local_id ON weather_records(local_id);";

    char *err_msg = 0;
    rc = sqlite3_exec(db, sql, 0, 0, &err_msg);
    if (rc != SQLITE_OK) {
        fprintf(stderr, "SQL error: %s\n", err_msg);
        sqlite3_free(err_msg);
        exit(1);
    }
}

// Handler for POST /sync
static void handle_sync(struct mg_connection *c, struct mg_http_message *hm) {
    cJSON *json = cJSON_ParseWithLength(hm->body.buf, hm->body.len);
    if (!json) {
        mg_http_reply(c, 400, "", "{\"error\": \"Invalid JSON\"}\n");
        return;
    }

    cJSON *records = cJSON_GetObjectItemCaseSensitive(json, "records");
    if (!cJSON_IsArray(records)) {
        cJSON_Delete(json);
        mg_http_reply(c, 400, "", "{\"error\": \"'records' is missing or not an array\"}\n");
        return;
    }

    sqlite3_exec(db, "BEGIN TRANSACTION;", NULL, NULL, NULL);

    const char *sql = "INSERT OR REPLACE INTO weather_records (local_id, timestamp, condition, latitude, longitude, location_name) VALUES (?, ?, ?, ?, ?, ?);";
    sqlite3_stmt *stmt;
    sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);

    int synced_count = 0;
    cJSON *record = NULL;
    cJSON_ArrayForEach(record, records) {
        cJSON *id = cJSON_GetObjectItemCaseSensitive(record, "local_id");
        cJSON *timestamp = cJSON_GetObjectItemCaseSensitive(record, "timestamp");
        cJSON *condition = cJSON_GetObjectItemCaseSensitive(record, "condition");
        cJSON *latitude = cJSON_GetObjectItemCaseSensitive(record, "latitude");
        cJSON *longitude = cJSON_GetObjectItemCaseSensitive(record, "longitude");
        cJSON *location_name = cJSON_GetObjectItemCaseSensitive(record, "location_name");

        if (cJSON_IsNumber(id) && cJSON_IsString(timestamp) && cJSON_IsString(condition) &&
            cJSON_IsNumber(latitude) && cJSON_IsNumber(longitude) && cJSON_IsString(location_name)) {
            
            sqlite3_bind_int(stmt, 1, id->valueint);
            sqlite3_bind_text(stmt, 2, timestamp->valuestring, -1, SQLITE_STATIC);
            sqlite3_bind_text(stmt, 3, condition->valuestring, -1, SQLITE_STATIC);
            sqlite3_bind_double(stmt, 4, latitude->valuedouble);
            sqlite3_bind_double(stmt, 5, longitude->valuedouble);
            sqlite3_bind_text(stmt, 6, location_name->valuestring, -1, SQLITE_STATIC);

            if (sqlite3_step(stmt) == SQLITE_DONE) {
                synced_count++;
            }
            sqlite3_reset(stmt);
        }
    }
    sqlite3_finalize(stmt);
    sqlite3_exec(db, "COMMIT;", NULL, NULL, NULL);

    cJSON_Delete(json);

    mg_http_reply(c, 200, "Content-Type: application/json\r\n", 
        "{\"status\":\"success\",\"synced_count\":%d}\n", synced_count);
}

// Handler for GET /api/records
static void handle_get_records(struct mg_connection *c, struct mg_http_message *hm) {
    const char *sql = "SELECT local_id, timestamp, condition, latitude, longitude, location_name FROM weather_records ORDER BY timestamp DESC;";
    sqlite3_stmt *stmt;
    
    if (sqlite3_prepare_v2(db, sql, -1, &stmt, NULL) != SQLITE_OK) {
        mg_http_reply(c, 500, "", "{\"error\": \"Database error\"}\n");
        return;
    }

    cJSON *array = cJSON_CreateArray();

    while (sqlite3_step(stmt) == SQLITE_ROW) {
        cJSON *item = cJSON_CreateObject();
        cJSON_AddNumberToObject(item, "local_id", sqlite3_column_int(stmt, 0));
        cJSON_AddStringToObject(item, "timestamp", (const char *)sqlite3_column_text(stmt, 1));
        cJSON_AddStringToObject(item, "condition", (const char *)sqlite3_column_text(stmt, 2));
        cJSON_AddNumberToObject(item, "latitude", sqlite3_column_double(stmt, 3));
        cJSON_AddNumberToObject(item, "longitude", sqlite3_column_double(stmt, 4));
        cJSON_AddStringToObject(item, "location_name", (const char *)sqlite3_column_text(stmt, 5));
        cJSON_AddItemToArray(array, item);
    }
    sqlite3_finalize(stmt);

    char *json_str = cJSON_PrintUnformatted(array);
    mg_http_reply(c, 200, "Content-Type: application/json\r\n", "%s", json_str);
    
    free(json_str);
    cJSON_Delete(array);
}

// Handler for GET /api/forecast
static void handle_get_forecast(struct mg_connection *c, struct mg_http_message *hm) {
    const char *sql = "SELECT condition, COUNT(*) as count FROM weather_records GROUP BY condition ORDER BY count DESC LIMIT 1;";
    sqlite3_stmt *stmt;
    
    int total_records = 0;
    sqlite3_stmt *stmt_total;
    if (sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM weather_records;", -1, &stmt_total, NULL) == SQLITE_OK) {
        if (sqlite3_step(stmt_total) == SQLITE_ROW) {
            total_records = sqlite3_column_int(stmt_total, 0);
        }
        sqlite3_finalize(stmt_total);
    }

    cJSON *resp = cJSON_CreateObject();
    
    if (total_records == 0) {
        cJSON_AddStringToObject(resp, "most_likely_condition", "Unknown");
        cJSON_AddNumberToObject(resp, "probability", 0.0);
        cJSON_AddNumberToObject(resp, "total_records_analyzed", 0);
    } else {
        if (sqlite3_prepare_v2(db, sql, -1, &stmt, NULL) == SQLITE_OK) {
            if (sqlite3_step(stmt) == SQLITE_ROW) {
                const char *condition = (const char *)sqlite3_column_text(stmt, 0);
                int count = sqlite3_column_int(stmt, 1);
                
                cJSON_AddStringToObject(resp, "most_likely_condition", condition ? condition : "Unknown");
                cJSON_AddNumberToObject(resp, "probability", (double)count / total_records * 100.0);
                cJSON_AddNumberToObject(resp, "total_records_analyzed", total_records);
            }
            sqlite3_finalize(stmt);
        }
    }

    char *json_str = cJSON_PrintUnformatted(resp);
    mg_http_reply(c, 200, "Content-Type: application/json\r\n", "%s", json_str);
    
    free(json_str);
    cJSON_Delete(resp);
}

// Main event handler
static void ev_handler(struct mg_connection *c, int ev, void *ev_data) {
    if (ev == MG_EV_HTTP_MSG) {
        struct mg_http_message *hm = (struct mg_http_message *) ev_data;

        if (mg_match(hm->uri, mg_str("/sync"), NULL)) {
            handle_sync(c, hm);
        } else if (mg_match(hm->uri, mg_str("/api/records"), NULL)) {
            handle_get_records(c, hm);
        } else if (mg_match(hm->uri, mg_str("/api/forecast"), NULL)) {
            handle_get_forecast(c, hm);
        } else {
            struct mg_http_serve_opts opts = {0};
            opts.root_dir = "static";
            mg_http_serve_dir(c, hm, &opts);
        }
    }
}

int main(void) {
    init_db();

    struct mg_mgr mgr;
    mg_mgr_init(&mgr);
    mg_http_listen(&mgr, "http://0.0.0.0:8888", ev_handler, &mgr);
    printf("Starting C Web Server on http://0.0.0.0:8888\n");

    for (;;) {
        mg_mgr_poll(&mgr, 1000);
    }

    mg_mgr_free(&mgr);
    sqlite3_close(db);
    return 0;
}
