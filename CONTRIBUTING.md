# Contributing

OpenKilda is an open source project and we welcome contributions of all sorts.
There are many ways to contribute, from reporting issues, submitting feature requests, 
improving the documentation or writing code.

# Contribution Guidelines

The following sections outline the process all changes to the Kilda
repositories go through.  All changes, regardless of whether they are from
newcomers to the community or from the core team follow the
same process and are given the same level of review.

- [Contributor License Agreements](#contributor-license-agreements)
- [Design Decisions and Documents](#design-decisions-and-documents)
- [Issues](#issues)
- [Contributing a Feature](#contributing-a-feature)
- [Pull Requests](#pull-requests)
- [Code Review](#code-review)
- [Code Style](#code-style)
- [Documentation Styleguide](#documentation-styleguide)

## Contributor License Agreements

Pull requests will be accepted from those who have filled out the 
appropriate [CLA](docs/contrib)

This is necessary because you own the copyright to your changes, even after your
contribution becomes part of this project. So this agreement simply gives us
permission to use and redistribute your contributions as part of the project.

## Design Decisions and Documents

We tend to design the critical pieces of the system. Most of this design lives
on the wiki and in `open-kilda/docs/design` folder. 

When you make a decision on how a feature or enhancement should be implemented, 
you'll need to go through the following steps:
1. Discuss your idea with the regular contributors. 
2. Once there's general agreement on the solution, create a design document and link to the corresponding issue.
3. Get the design being approved by the appropriate working groups or regular contributors.
4. Implement the solution and submit your code changes.

Design documents are part of the project documentation and should be shared with the community 
by adding the doc with related files to `open-kilda/docs/design` folder.

The general requirements for design documentation:
- Describe the current and expected behavior.
- Provide arguments for and against each option / solution.
- For a new feature, corresponding use cases are listed.
- Describe the changes in the processes, flows, interactions. Even for a newly introduced. Use sequence diagrams.
- Describe the new or restructured components, classes, modules. Use class or component diagrams.
- Document the implementation approach for custom non-trivial algorithms. Use activity diagrams, flowcharts, pseudocode, etc.
- Specify which API will be affected and what the changes are.

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

## Contributing a Feature

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

## Pull Requests

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
- Ensure the changes follow the OpenKilda coding style.
- Modify the documentation as necessary.
- Verify the entire CI process (building and testing) work.

While there may be exceptions, the general rule is that all PRs should
be 100% complete - meaning they should include all test cases and documentation
changes related to the change.

When ready, and assuming you've signed the CLA, submit the PR.

### Code Review

Every pull request must pass code review before merging. Getting a review 
means that a regular contributor (someone with commit access) has approved your PR, 
and you have addressed all their feedback.

It is important to understand that code review is about improving the quality of 
contributed code, and nothing else. 
Further, code review is highly important, as every line of code that's committed comes 
with a burden of maintenance. Code review helps minimize the burden of that maintenance.

### Code Style

When submitting code, please make every effort to follow existing OpenKilda conventions 
and style in order to keep the code as readable and clean as possible. 

OpenKilda uses [Checkstyle](http://checkstyle.sourceforge.net/) to make sure that all the code follows those standards. 
Checkstyle failures during compilation indicate errors in your style and must be addressed before submitting the changes.

Before start contributing with new code it is recommended to install IntelliJ CheckStyle-IDEA plugin 
and configure it to use [OpenKilda's checkstyle configuration file](services/src/checkstyle/README.md). 

### Documentation Styleguide

Use [Markdown](https://daringfireball.net/projects/markdown) for documenting solution proposals, manuals, guidelines, 
readme files, etc.

Use [Plant UML](https://sourceforge.net/projects/plantuml/) as a UML diagram render. 
The text definition (source) of a diagram must be delivered along with the rendered version. 