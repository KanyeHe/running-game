CREATE TABLE IF NOT EXISTS users
(
    id
    VARCHAR
(
    36
) PRIMARY KEY, token VARCHAR
(
    64
) NOT NULL UNIQUE, name VARCHAR
(
    96
) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
CREATE TABLE IF NOT EXISTS rooms
(
    id
    VARCHAR
(
    64
) PRIMARY KEY, version BIGINT NOT NULL, snapshot JSON NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    );
CREATE TABLE IF NOT EXISTS matches
(
    id
    VARCHAR
(
    96
) PRIMARY KEY, room_id VARCHAR
(
    64
) NOT NULL, winner INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX idx_match_room
(
    room_id
)
    );
CREATE TABLE IF NOT EXISTS player_results
(
    match_id
    VARCHAR
(
    96
) NOT NULL, player_id VARCHAR
(
    36
) NOT NULL, seat INT NOT NULL,
    remaining INT NOT NULL, score INT NOT NULL, PRIMARY KEY
(
    match_id,
    player_id
), INDEX idx_result_player
(
    player_id
)
    );
CREATE TABLE IF NOT EXISTS play_records
(
    match_id
    VARCHAR
(
    96
) NOT NULL,event_no BIGINT NOT NULL,player_seat INT NOT NULL,state_version BIGINT NOT NULL,
    event_type VARCHAR
(
    32
) NOT NULL,payload JSON NOT NULL, PRIMARY KEY
(
    match_id,
    event_no
), INDEX idx_play_version
(
    match_id,
    state_version
)
    );
CREATE TABLE IF NOT EXISTS score_ledger
(
    match_id
    VARCHAR
(
    96
) NOT NULL,player_id VARCHAR
(
    36
) NOT NULL,amount INT NOT NULL,
    PRIMARY KEY
(
    match_id,
    player_id
), INDEX idx_score_player
(
    player_id
)
    );
CREATE TABLE IF NOT EXISTS bomb_ledger
(
    match_id
    VARCHAR
(
    96
) NOT NULL,entry_no BIGINT NOT NULL,trick BIGINT NOT NULL,player_seat INT NOT NULL,
    amount INT NOT NULL,reverses BIGINT NULL,payload JSON NOT NULL, PRIMARY KEY
(
    match_id,
    entry_no
), INDEX idx_bomb_trick
(
    match_id,
    trick
)
    );
CREATE TABLE IF NOT EXISTS operations
(
    room_id
    VARCHAR
(
    64
) NOT NULL,player_id VARCHAR
(
    36
) NOT NULL,command_seq BIGINT NOT NULL,request_id VARCHAR
(
    64
) NOT NULL,
    expected_version BIGINT NOT NULL,accepted_version BIGINT NOT NULL,payload JSON NOT NULL,
    PRIMARY KEY
(
    room_id,
    player_id,
    command_seq
), INDEX idx_operation_version
(
    room_id,
    accepted_version
)
    );
