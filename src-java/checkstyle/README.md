# Coding style guide

## Installing the OpenKilda coding style settings in IntelliJ IDEA

Download the [intellij-java-openkilda-style.xml](https://github.com/telstra/open-kilda/tree/master/src-java/checkstyle/intellij-java-openkilda-style.xml) file.

Go into Settings -> Editor -> Code Style. Click on Import Scheme and choose the downloaded style file. 

Select OpenKildaStyle as actual coding style.

Install CheckStyle-IDEA plugin for IntelliJ IDEA.

Go into Settings -> Tools -> CheckStyle-IDEA and add new profile using [checkstyle.xml](https://github.com/telstra/open-kilda/tree/master/src-java/checkstyle/checkstyle.xml) file.
In the opened dialog set checkstyle.header.file as [checkstyle-header.txt](https://github.com/telstra/open-kilda/tree/master/src-java/checkstyle/checkstyle.txt)
and checkstyle.suppression.file as [checkstyle-suppressions.xml](https://github.com/telstra/open-kilda/tree/master/src-java/checkstyle/checkstyle-suppressions.xml)

Now you can run inspection and check code style issues.
Please check issues IDEA highlights for the code modification and fix it.
Most of the time there would be a hint when hover the highlighted part in IDEA.

To check coding style issues before making the commit, you can run:
`make checkstyle`
