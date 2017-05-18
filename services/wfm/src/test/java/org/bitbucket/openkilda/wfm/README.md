# Testing Notes

## Execution Tips

You can use the following pattern to target specific tests during ```mvn test```:

```
mvn "-Dtest=org.bitbucket.openkilda.wfm.SimpleKafka*" test
# another example, same test
mvn "-Dtest=SimpleKafka*" test
```