ALTER TABLE games
    ADD CONSTRAINT unique_games_name UNIQUE (name);