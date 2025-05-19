#!/bin/bash 
javac LoadTest.java 
echo $SECONDS 
java LoadTest 127.0.0.1 8087 11 GET 1000 & 
java LoadTest 127.0.0.1 8087 11 GET 1000 & 
java LoadTest 127.0.0.1 8087 11 GET 1000 & 
java LoadTest 127.0.0.1 8087 12 GET 1000 & 
wait $(jobs -p) 
echo $SECONDS
