CREATE TABLE iac_files
(
    file_id    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    file_name  VARCHAR(256) NOT NULL,
    hash       VARCHAR(64)  NOT NULL,

    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX iac_files_file_name_idx ON iac_files (file_name);
