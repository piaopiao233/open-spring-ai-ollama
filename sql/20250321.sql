CREATE TABLE "public"."chat_message" (
                                         "id" int8 NOT NULL DEFAULT nextval('chat_message_id_seq'::regclass),
                                         "type" int2 NOT NULL,
                                         "session_id" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
                                         "record_id" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
                                         "content" text COLLATE "pg_catalog"."default" NOT NULL,
                                         "create_time" timestamp(0) NOT NULL,
                                         CONSTRAINT "chat_message_pkey" PRIMARY KEY ("id")
)
;

ALTER TABLE "public"."chat_message"
    OWNER TO "postgres";

CREATE INDEX "index_session_id" ON "public"."chat_message" USING btree (
    "session_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

COMMENT ON COLUMN "public"."chat_message"."type" IS '0：系统  1：用户 2：AI';

COMMENT ON COLUMN "public"."chat_message"."session_id" IS '会话id';

COMMENT ON COLUMN "public"."chat_message"."record_id" IS '对话id';

COMMENT ON COLUMN "public"."chat_message"."content" IS '对话内容';

COMMENT ON COLUMN "public"."chat_message"."create_time" IS '创建时间';

CREATE TABLE "public"."vector_store" (
                                         "id" uuid NOT NULL DEFAULT uuid_generate_v4(),
                                         "content" text COLLATE "pg_catalog"."default",
                                         "metadata" json,
                                         "embedding" "public"."vector",
                                         CONSTRAINT "vector_store_pkey" PRIMARY KEY ("id")
)
;

ALTER TABLE "public"."vector_store"
    OWNER TO "postgres";

CREATE INDEX "vector_store_embedding_idx" ON "public"."vector_store" (
                                                                      "embedding" "public"."vector_cosine_ops" ASC NULLS LAST
    );
