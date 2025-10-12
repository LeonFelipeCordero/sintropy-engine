--liquibase formatted sql

--changeset LeonFelipeCordero:005-create-publication logicalFilePath:db/changelog/005-create-publication.sql

create publication messages_pub_insert_only
    for table messages
    with (publish = 'insert');
