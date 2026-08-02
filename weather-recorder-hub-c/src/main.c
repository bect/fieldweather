#include "../lib/mongoose/mongoose.h"
#include "../lib/cjson/cJSON.h"
#include "../lib/sqlite/sqlite3.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static sqlite3 *db;
static char turso_db_url[256] = {0};
static char turso_token[512] = {0};

static void load_env() {
    FILE *f = fopen(".env", "r");
    if (!f) return;
    char line[1024];
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "TURSO_DB_URL=", 13) == 0) {
            sscanf(line, "TURSO_DB_URL=%255[^\n]", turso_db_url);
        } else if (strncmp(line, "TURSO_TOKEN=", 12) == 0) {
            sscanf(line, "TURSO_TOKEN=%511[^\n]", turso_token);
        }
    }
    fclose(f);
}

static void pull_timer_cb(void *arg) {
    (void) arg; // Unused
    if (strlen(turso_db_url) > 0 && strlen(turso_token) > 0) {
        char final_url[512];
        if (strncmp(turso_db_url, "libsql://", 9) == 0) {
            snprintf(final_url, sizeof(final_url), "https://%s/v2/pipeline", turso_db_url + 9);
        } else {
            snprintf(final_url, sizeof(final_url), "%s/v2/pipeline", turso_db_url);
        }

        char cmd[2048];
        const char *payload = "{\"requests\":[{\"type\":\"execute\",\"stmt\":{\"sql\":\"SELECT local_id, timestamp, condition, latitude, longitude, location_name FROM weather_records\"}}]}";
        
        snprintf(cmd, sizeof(cmd), "curl -s -X POST \"%s\" -H \"Authorization: Bearer %s\" -H \"Content-Type: application/json\" -d '%s'",
                 final_url, turso_token, payload);

        FILE *fp = popen(cmd, "r");
        if (fp == NULL) {
            printf("Failed to run curl command\n");
            return;
        }

        char *buffer = malloc(65536); // 64KB max for now
        if (!buffer) {
            pclose(fp);
            return;
        }

        size_t len = fread(buffer, 1, 65535, fp);
        buffer[len] = '\0';
        pclose(fp);

        cJSON *json = cJSON_Parse(buffer);
        if (json) {
            cJSON *results = cJSON_GetObjectItemCaseSensitive(json, "results");
            if (cJSON_IsArray(results)) {
                cJSON *first_result = cJSON_GetArrayItem(results, 0);
                cJSON *response = cJSON_GetObjectItemCaseSensitive(first_result, "response");
                cJSON *result = cJSON_GetObjectItemCaseSensitive(response, "result");
                cJSON *rows = cJSON_GetObjectItemCaseSensitive(result, "rows");
                
                if (cJSON_IsArray(rows)) {
                    sqlite3_exec(db, "BEGIN TRANSACTION;", NULL, NULL, NULL);
                    const char *sql = "INSERT OR REPLACE INTO weather_records (local_id, timestamp, condition, latitude, longitude, location_name) VALUES (?, ?, ?, ?, ?, ?);";
                    sqlite3_stmt *stmt;
                    sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);

                    cJSON *row;
                    cJSON_ArrayForEach(row, rows) {
                        cJSON *id_obj = cJSON_GetArrayItem(row, 0);
                        cJSON *ts_obj = cJSON_GetArrayItem(row, 1);
                        cJSON *cond_obj = cJSON_GetArrayItem(row, 2);
                        cJSON *lat_obj = cJSON_GetArrayItem(row, 3);
                        cJSON *lon_obj = cJSON_GetArrayItem(row, 4);
                        cJSON *loc_obj = cJSON_GetArrayItem(row, 5);
                        
                        if (id_obj && ts_obj && cond_obj && lat_obj && lon_obj && loc_obj) {
                            cJSON *id_val = cJSON_GetObjectItem(id_obj, "value");
                            cJSON *ts_val = cJSON_GetObjectItem(ts_obj, "value");
                            cJSON *cond_val = cJSON_GetObjectItem(cond_obj, "value");
                            cJSON *lat_val = cJSON_GetObjectItem(lat_obj, "value");
                            cJSON *lon_val = cJSON_GetObjectItem(lon_obj, "value");
                            cJSON *loc_val = cJSON_GetObjectItem(loc_obj, "value");

                            if (id_val && ts_val && cond_val && lat_val && lon_val && loc_val) {
                                int id = cJSON_IsString(id_val) ? atoi(id_val->valuestring) : (cJSON_IsNumber(id_val) ? id_val->valueint : 0);
                                const char *ts = cJSON_IsString(ts_val) ? ts_val->valuestring : "";
                                const char *cond = cJSON_IsString(cond_val) ? cond_val->valuestring : "";
                                double lat = cJSON_IsString(lat_val) ? atof(lat_val->valuestring) : (cJSON_IsNumber(lat_val) ? lat_val->valuedouble : 0.0);
                                double lon = cJSON_IsString(lon_val) ? atof(lon_val->valuestring) : (cJSON_IsNumber(lon_val) ? lon_val->valuedouble : 0.0);
                                const char *loc = cJSON_IsString(loc_val) ? loc_val->valuestring : "";

                                sqlite3_bind_int(stmt, 1, id);
                                sqlite3_bind_text(stmt, 2, ts, -1, SQLITE_STATIC);
                                sqlite3_bind_text(stmt, 3, cond, -1, SQLITE_STATIC);
                                sqlite3_bind_double(stmt, 4, lat);
                                sqlite3_bind_double(stmt, 5, lon);
                                sqlite3_bind_text(stmt, 6, loc, -1, SQLITE_STATIC);
                                sqlite3_step(stmt);
                                sqlite3_reset(stmt);
                            }
                        }
                    }
                    sqlite3_finalize(stmt);
                    sqlite3_exec(db, "COMMIT;", NULL, NULL, NULL);
                }
            }
            cJSON_Delete(json);
        }
        free(buffer);
    }
}

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
        "location_name TEXT);"
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
    char location[256] = {0};
    if (mg_http_get_var(&hm->query, "location", location, sizeof(location)) <= 0) {
        mg_http_reply(c, 400, "", "{\"error\": \"Missing 'location' parameter\"}\n");
        return;
    }

    sqlite3_stmt *stmt;
    char prev1[64] = {0};
    char prev2[64] = {0};
    int num_records = 0;
    int total_location_records = 0;

    // Get total records for location
    if (sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM weather_records WHERE location_name = ?;", -1, &stmt, NULL) == SQLITE_OK) {
        sqlite3_bind_text(stmt, 1, location, -1, SQLITE_STATIC);
        if (sqlite3_step(stmt) == SQLITE_ROW) {
            total_location_records = sqlite3_column_int(stmt, 0);
        }
        sqlite3_finalize(stmt);
    }

    // Get the two most recent conditions
    const char *sql_recent = "SELECT condition FROM weather_records WHERE location_name = ? ORDER BY timestamp DESC LIMIT 2;";
    if (sqlite3_prepare_v2(db, sql_recent, -1, &stmt, NULL) == SQLITE_OK) {
        sqlite3_bind_text(stmt, 1, location, -1, SQLITE_STATIC);
        if (sqlite3_step(stmt) == SQLITE_ROW) {
            strncpy(prev1, (const char *)sqlite3_column_text(stmt, 0), sizeof(prev1)-1);
            num_records++;
            if (sqlite3_step(stmt) == SQLITE_ROW) {
                strncpy(prev2, (const char *)sqlite3_column_text(stmt, 0), sizeof(prev2)-1);
                num_records++;
            }
        }
        sqlite3_finalize(stmt);
    }

    cJSON *resp = cJSON_CreateObject();

    if (num_records == 0) {
        cJSON_AddStringToObject(resp, "most_likely_condition", "Unknown");
        cJSON_AddNumberToObject(resp, "probability", 0.0);
        cJSON_AddNumberToObject(resp, "total_records_analyzed", 0);
        cJSON_AddStringToObject(resp, "model_used", "None");
    } else {
        const char *sql_2nd_order = 
            "SELECT current_condition, COUNT(*) as transition_count FROM ("
            "  SELECT condition as current_condition, "
            "         LAG(condition, 1) OVER (ORDER BY timestamp ASC) as prev1_condition, "
            "         LAG(condition, 2) OVER (ORDER BY timestamp ASC) as prev2_condition "
            "  FROM weather_records WHERE location_name = ?"
            ") WHERE prev1_condition = ? AND prev2_condition = ? GROUP BY current_condition;";

        const char *sql_1st_order = 
            "SELECT current_condition, COUNT(*) as transition_count FROM ("
            "  SELECT condition as current_condition, "
            "         LAG(condition, 1) OVER (ORDER BY timestamp ASC) as prev1_condition "
            "  FROM weather_records WHERE location_name = ?"
            ") WHERE prev1_condition = ? GROUP BY current_condition;";
            
        const char *sql_0th_order = 
            "SELECT condition as current_condition, COUNT(*) as transition_count FROM weather_records "
            "WHERE location_name = ? GROUP BY condition;";

        char best_condition[64] = "Unknown";
        int max_count = 0;
        int total_transitions = 0;
        int model = 2;
        
        // Try 2nd Order
        if (num_records == 2 && sqlite3_prepare_v2(db, sql_2nd_order, -1, &stmt, NULL) == SQLITE_OK) {
            sqlite3_bind_text(stmt, 1, location, -1, SQLITE_STATIC);
            sqlite3_bind_text(stmt, 2, prev1, -1, SQLITE_STATIC);
            sqlite3_bind_text(stmt, 3, prev2, -1, SQLITE_STATIC);
            while (sqlite3_step(stmt) == SQLITE_ROW) {
                int count = sqlite3_column_int(stmt, 1);
                total_transitions += count;
                if (count > max_count) {
                    max_count = count;
                    strncpy(best_condition, (const char *)sqlite3_column_text(stmt, 0), sizeof(best_condition)-1);
                }
            }
            sqlite3_finalize(stmt);
        }

        // Fallback to 1st Order
        if (total_transitions == 0 && num_records >= 1) {
            model = 1;
            if (sqlite3_prepare_v2(db, sql_1st_order, -1, &stmt, NULL) == SQLITE_OK) {
                sqlite3_bind_text(stmt, 1, location, -1, SQLITE_STATIC);
                sqlite3_bind_text(stmt, 2, prev1, -1, SQLITE_STATIC);
                while (sqlite3_step(stmt) == SQLITE_ROW) {
                    int count = sqlite3_column_int(stmt, 1);
                    total_transitions += count;
                    if (count > max_count) {
                        max_count = count;
                        strncpy(best_condition, (const char *)sqlite3_column_text(stmt, 0), sizeof(best_condition)-1);
                    }
                }
                sqlite3_finalize(stmt);
            }
        }
        
        // Fallback to 0th Order
        if (total_transitions == 0) {
            model = 0;
            if (sqlite3_prepare_v2(db, sql_0th_order, -1, &stmt, NULL) == SQLITE_OK) {
                sqlite3_bind_text(stmt, 1, location, -1, SQLITE_STATIC);
                while (sqlite3_step(stmt) == SQLITE_ROW) {
                    int count = sqlite3_column_int(stmt, 1);
                    total_transitions += count;
                    if (count > max_count) {
                        max_count = count;
                        strncpy(best_condition, (const char *)sqlite3_column_text(stmt, 0), sizeof(best_condition)-1);
                    }
                }
                sqlite3_finalize(stmt);
            }
        }

        double probability = total_transitions > 0 ? ((double)max_count / total_transitions * 100.0) : 0.0;
        
        cJSON_AddStringToObject(resp, "most_likely_condition", best_condition);
        cJSON_AddNumberToObject(resp, "probability", probability);
        cJSON_AddNumberToObject(resp, "total_records_analyzed", total_location_records);
        
        if (model == 2) cJSON_AddStringToObject(resp, "model_used", "2nd-Order Markov Chain");
        else if (model == 1) cJSON_AddStringToObject(resp, "model_used", "1st-Order Markov Chain");
        else cJSON_AddStringToObject(resp, "model_used", "0th-Order Baseline");
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
    load_env();
    if (strlen(turso_db_url) > 0) {
        printf("Turso Sync Enabled: %s\n", turso_db_url);
        mg_timer_add(&mgr, 60000, MG_TIMER_REPEAT | MG_TIMER_RUN_NOW, pull_timer_cb, &mgr);
    }

    for (;;) {
        mg_mgr_poll(&mgr, 1000);
    }

    mg_mgr_free(&mgr);
    sqlite3_close(db);
    return 0;
}
