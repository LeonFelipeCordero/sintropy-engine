create publication messages_pub_insert_only
    for table messages
    with (publish = 'insert');
