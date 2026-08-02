CC ?= gcc
CFLAGS = -O3 -Wall -Wextra -pthread -Ilib/sqlite -Ilib/cjson -Ilib/mongoose
LDFLAGS = -lm -ldl

SRCS = src/main.c lib/mongoose/mongoose.c lib/cjson/cJSON.c lib/sqlite/sqlite3.c
OBJS = $(SRCS:.c=.o)
TARGET = hub

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)
