# Contribution guidelines

The following sections outline the process all changes to the Kilda
repositories go through.  All changes, regardless of whether they are from
newcomers to the community or from the core team follow the
same process and are given the same level of review.

- [Contributor license agreements](#contributor-license-agreements)
- [Design Documents](#design-docs)
- [Issues](#issues)
- [Contributing a feature](#contributing-a-feature)
- [Pull requests](#pull-requests)
- [Code Style](#code-style)

## Contributor license agreements

Pull requests will be accepted from those who have filled out the 
appropriate [CLA](docs/contrib)

This is necessary because you own the copyright to your changes, even after your
contribution becomes part of this project. So this agreement simply gives us
permission to use and redistribute your contributions as part of the project.

## Design Documents

We tend to design the critical pieces of the system. Most of this design lives
in the wiki. We'll work on a better location for this work and the process for
including this within the project. 

## Issues

GitHub issues can be used to report bugs or feature requests.

When reporting a bug please include the following key pieces of information:
- the version of the project you were using (e.g. version number,
  git commit, ...)
- operating system you are using
- the exact, minimal, steps needed to reproduce the issue.
  Submitting a 5 line script will get a much faster response from the team
  than one that's hundreds of lines long.

See the next section for information about using issues to submit feature requests.

## Contributing a feature

In order to contribute a feature you'll need to go through the following steps:
- Create a GitHub issue to track the discussion. The issue should include information 
about the requirements and use cases that it is trying to address.
- Include a discussion of the proposed design and technical details of the implementation in the issue.
- Once there is general agreement on the technical direction, submit a PR.

If you would like to skip the process of submitting an issue and
instead would prefer to just submit a pull request with your desired
code changes then that's fine. But keep in mind that there is no guarantee
of it being accepted and so it is usually best to get agreement on the
idea/design before time is spent coding it. However, sometimes seeing the
exact code change can help focus discussions, so the choice is up to you.

## Pull requests

If you're working on an existing issue, simply respond to the issue and express
interest in working on it. This helps other people know that the issue is
active, and hopefully prevents duplicated efforts.

To submit a proposed change:
- Fork the repository.
- Create a new branch for your changes.
- Develop the code/fix.
- Add new test cases. In the case of a bug fix, the tests should fail
  without your code changes. For new features try to cover as many
  variants as reasonably possible.
- Modify the documentation as necessary.
- Verify the entire CI process (building and testing) work.

While there may be exceptions, the general rule is that all PRs should
be 100% complete - meaning they should include all test cases and documentation
changes related to the change.

When ready, and assuming you've signed the CLA, submit the PR.

### Code Style

When submitting code, please make every effort to follow existing OpenKilda conventions 
and style in order to keep the code as readable and clean as possible. 

OpenKilda uses [Checkstyle](http://checkstyle.sourceforge.net/) to make sure that all the code follows those standards. 
Checkstyle failures during compilation indicate errors in your style and must be addressed before submitting the changes.

Before start contributing with new code it is recommended to install IntelliJ CheckStyle-IDEA plugin 
and configure it to use [OpenKilda's checkstyle configuration file](services/src/checkstyle/README.md). 
