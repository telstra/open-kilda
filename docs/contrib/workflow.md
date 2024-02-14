# Contribution to OpenKilda project

Anyone can contribute to the OpenKilda project. This instruction describes how
OpenKilda contribution looks like: from an idea to OpenKilda release.

# Workflow

## Idea

Before adding a new feature or making an improvement to OpenKilda,
it is recommended to discuss the idea with other contributors first.
To find current active contributors, you can view a list of
[open pull requests](https://github.com/telstra/open-kilda/pulls) and select
the author of a recent pull request to send a message about your idea.
They will invite you to a forum for idea discussions.

If you don't have an idea, but you still want to contribute, please take a look on a
list of [open issues](https://github.com/telstra/open-kilda/issues). If you find
an open and unassigned issue (of course you will) you can assign it to yourself and
start to work on it.

### Design

If you are working on a small feature, fix or improvement you can skip the design step,
but please create an issue on GitHub with short description of it.
But if you are working on a big feature, especially which changes API, you must
create a design document first. Examples of design documents can be found [here](../design).

## Development

### Git branches

To make any changes in OpenKilda repository you need to create a git branch.
There are 2 main branches in the repository:

* `develop` - contains changes which were not yet released
* `master` - contains last [release](https://github.com/telstra/open-kilda/releases)

Please checkout your branch from `develop` branch.

### Branch name

There are no strict branch name restrictions. The only rule is to make branch name meaningful.
In practice, we are using the following prefixes for branches:

* `docs/` - for adding/updating the documentation
* `feature/` - for adding new features
* `fix/` - for fixing current codebase
* `test/` - for tests
* `release` - for releases (only a release manager can create such branches)
* `hotfix` - for hotfixes which must be released ASAP (only a release manager can create such branches)
* `chore/` - for version upgrades, bump up, formatting, cleaning useless code, etc.

Branch name after the prefix must consist of 2 parts:

1. GitHub issue id (if there is an issue for your changes)
2. Very short description of your changes

Good branch names:

* `doc/contribution_guide`
* `feature/1234_ha_flow_create`
* `fix/4567_incorrect_latency_validation`
* `test/1234_ha_flow_functional_tests`
* `release-1.2.3`
* `hotfix-1.2.4`

Bad branch names:

* `guide`
* `refactoring`
* `added_tests`
* `my_mega_fix`
* `doc/how_to_contribute_in_kilda_if_you_cant_come_up_with_a_short_name_for_a_git_branch`

### Commits

The best practice is to have one commit per one branch. If you need to change
your code after it was pushed - just amend your new changes into your last commit
(`git commit --amend`).

If you will, you can have several commits in your branch but please use meaningful
commit titles for them. First commit must describe a main idea of your pull request.
Rest commit titles must describe changes which were added after the fist commit.

Good commit titles in a branch:

```
Add HA flow create operation
Add unit tests for HA flow create
Fix checkstyle issues
Remove unused files
```

Bad commit titles in a branch:

```
Added HA flow create operation
fix
fix
fix
```

```
Add HA flow create operation
Add HA flow create operation
Add HA flow create operation
```

```
Add HA flow create operation
debug
Fix issues after code review
```

### Commit message

Commit message consist of several parts:

* Title - one sentence with describes the main idea of your changes in
  [imperative mood](https://git.kernel.org/pub/scm/git/git.git/tree/Documentation/SubmittingPatches?id=HEAD#n183)
* Description - several sentences which describe your changes and give
  some addition information. Try to explain what you do, and why are you doing it.
* Tags - helps GitHub to link PRs and issues

When you create a new pull request (PR) from your branch on GitHub it takes
commit message of your first commit in a branch as PR description.
GitHub can handle a set of tags from your commit message.

Mostly we are using the following tags:

1. `Closes #1234` - shows that your pull request closes GitHub issue #1234.
   When your PR will be merged GitHub will close issue #1234 automatically
2. `Related to #1234` - shows that your pull request related to GitHub issue
   #1234 but do not close it. For example when you develop some big feature you can
   have several PRs with different parts of your feature. Only last PR must have tag
   `Closes`. Rest of your PRs must use tag `Related to` to do not close GitHub issue
   when they will be merged.

The previous paragraph says that you can have several commit in your PR. Only the first one
must contain full commit message with title, description and tags. Rest commits
in your branch can have only meaningful title. But remember that the best practice is to
have only one commit per branch/PR to do not face with the problem of meaningless
commit titles at all.

## Review

To show your changes to the other contributors you must
[create](https://github.com/telstra/open-kilda/compare) a GitHub pull request.

### GitHub pull request

GitHub requires two branches for a pull request: base branch and your branch.
Base branch for most of the PRs is `develop` branch.
But if you want to split your changes on several PRs you can choose some other branch
as a base.

#### Examples 1:

A developer has branch a `feature/ha_flows` with a new feature and wants to create a PR.
The developer creates new GitHub PR from branch `feature/ha_flows` with the base branch `develop`.
A QA has branch `test/ha_flows_func_tests` with tests for developer's feature. The developer
creates new PR from the branch `test/ha_flows_func_tests` with the base branch
`feature/ha_flows`.

#### Examples 2:

A developer is working on some big feature. If he/she put all changes into one PR it can
have several thousand lines of code. It will be very hard to review. It this case
a developer should split his/her changes into several PRs:

| PR title                                      | Base branch                 | Branch                      | Tags             |
|-----------------------------------------------|-----------------------------|-----------------------------|------------------|
| HA flows Part 1: Added API objects            | `develop`                   | `feature/ha_flow_api`       | Related to #1234 |
| HA flows Part 2: Added messaging objects      | `feature/ha_flow_api`       | `feature/ha_flow_messaging` | Related to #1234 |
| HA flows Part 3: Implemented create operation | `feature/ha_flow_messaging` | `feature/ha_flow_create`    | Related to #1234 |
| HA flows Part 4: Implemented delete operation | `feature/ha_flow_create`    | `feature/ha_flow_delete`    | Closes #1234     |

If your PR has more than 1000 lines of code - please try to split in into several PRs.
It's not mandatory, but it's recommended. If your PR is 1500 lines with almost same data objects
there is no much sense to split it. Separation can be very different. Main rule - use a common sense.

### Pull request labels

Your PR must have labels. We have different label categories.

1. PR type. One of three labels: `feature`, `bugfix`, `improvement`.
   Pick one which describes your changes best. If your PR consists of tests please use
   the same labels: `feature` for adding tests, `bugfix` for fixing tests, `improvement`
   for adding new helper methods/refactoring.
2. Area. `area/testing`, `area/docs`, etc. Describes area which you are changing.
3. Migration. If your PR contains database migration, you must specify label `DB migration`.
4. Review status. Label `On review` must be set if your changes are ready for review.
   If work is still in progress - do not use this label.
5. Test status. Labels `Ready for Testing` and `tested` were made for QAs to track test
   status of a PR. These labels will be described in [paragraph](#pull-request-life-cycle)
   bellow.
6. Merge status. If your PR has green tests, 2 approvals and label `tested` - it can be marked
   as `Ready to merge`. It means that this PR can be included into next Release any minute.
7. Release status. If your PR was merged, a release manager will change `Ready to merge` label
   with `Next Release`. You can't set label `Next Release`. Only release menager can.

### Pull request life cycle

Complete process of PR life cycle looks like:

1. A developer opens a PR with labels from [categories](#pull-request-labels) 1-3.
2. When the PR is no longer a draft, the developer adds reviewers and puts label `On Review`.
3. When the task is completed and the PR can be built and deployed, a developer adds
   `Ready for Testing` label. It would be a signal for testers to start working on the
   PR verification.
4. After a tester verified the current state of code in the PR (it's not necessary
   to have the approved PR at this moment), the tester replaces `Ready for Testing`
   label with `tested`.
5. If the tester finds the task is not properly solved (contains bugs, not covers
   all requirements to close the task or other concerns), the tester removes the
   `Ready for Testing` label and returns it to the development with the comment
   which describe what is required to be fixed.
6. If a developer needs to change something in a tested PR, then after pushing changes,
   the developer removes `tested` label and returns `Ready for Testing` label.
   This signals to testers that this PR was changed and new testing is needed
   (the test code might require changes as well). Steps 4-6 can be repeated several times.
7. When a PR is approved by 2 contributors, the label `tested` was put last time
   AFTER the pushing of last commit to PR, the developer replaces the `On Review`
   label with `Ready to Merge` one.
8. From this moment a Release manager can merge the PR to include it into release
   branch. When the PR will be merged the release manager will change `Ready to Merge`
   label with `Next Release`. Only a release manager can do it.

When you mark your PR as `Ready to Merge` - your job on the PR is done. You can start to work
on a new one.

### Pull request merging

Common rule - do not merge any PR without asking a Release Manager.
Release manager is a person who is responsible for a release. He/she must
know exactly which PRs will be included in a release. Only a Release Manager
has rights to merge PR in `develop` branch. If your PR is based on some
other branch (like in the [example](#examples-2)) and you want to merge it into
base branch - please ask a Release manager for permission to do it.
Even if it is PR with tests for some feature. It's important because Release
Manager is responsible for creation of release notes. Usually he/she collects
a list of PRs for current release with the help of label `Next Release`.
You can forget to use this label. So please inform current Release Manager
if you want to merge anything.

Usually a Release Manager removes your branch after merging of your PR.
It allows to update base branch in GitHub PRs, which are dependent from your PR's branch. 
So be ready that your branch will be deleted after merging.
