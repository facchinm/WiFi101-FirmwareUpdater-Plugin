# Download keyutil.jar from https://github.com/use-sparingly/keyutil
java -jar keyutil-0.4.0.jar --new-keystore trustStore.jks --password arduino --import-pem-file roots.pem  -i
