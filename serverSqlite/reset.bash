#!/bin/bash

mkdir /virtual/$USER
rm /virtual/$USER/example.db

sqlite3 /virtual/$USER/example.db < schema.sql