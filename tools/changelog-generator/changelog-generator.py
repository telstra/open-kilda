from github import Github
from git import Repo
from jinja2 import Environment, FileSystemLoader
import re
import datetime
import click


def get_issues_from_pr(p):
    return [m.groups()[0] for m in re.finditer(r"#(\d+)", p.body)]


def generate_topic_list(labels):
    r = set()
    for l in labels:
        if l == 'area/gui':
            r.add('gui')
        elif l == 'C/NB':
            r.add('northbound')
        elif l == 'area/api':
            r.add('api')
        elif l == 'area/config':
            r.add('configuration')
        elif l == 'C/FL' or l == 'area/floodlight':
            r.add('floodlight')
        elif l == 'area/docs':
            r.add('docs')
        elif l == 'area/testing':
            r.add('tests')
        elif l in ['area/storm',
                   'C/STATS',
                   'C/EVENT',
                   'C/ISLLATENCY',
                   'C/FLOW',
                   'C/PORTSTATE',
                   'C/NBWORKER',
                   'C/PING',
                   'C/SWMANAGER',
                   'C/NETWORK',
                   'C/ROUTER',
                   'C/REROUTE']:
            r.add('storm-topologies')
    return r


def get_pull_request_id(commit):
    regex = r"Merge pull request #(\d+) from "
    matches = re.finditer(regex, commit.message)
    try:
        return int(next(m.groups()[0] for m in matches))
    except StopIteration:
        pass


def lable_filter(label):
    return all([label not in ['feature', 'bug', 'improvement', 'Next Release', 'Ready to Merge'],
                not label.startswith('priority/')
                ])


@click.command()
@click.option('--github-token', required=True, help='token from gh, look https://help.github.com/en/articles/creating'
                                                    '-a-personal-access-token-for-the-command-line')
@click.option('--new-version', required=True, help='new version in format 1.2.3')
@click.option('--from-version', help='version of last release in format 1.2.3')
@click.option('--git-rev-list', help='custom revision list, look man git-rev-list')
def main(github_token, new_version, from_version, git_rev_list):
    from_ver = from_version
    to_ver = new_version

    if from_ver is None and git_rev_list is None:
        raise click.BadParameter('Must set --from-version or --git-rev-list')

    if git_rev_list is None:
        git_rev_list = f'v{from_ver}..upstream/release-{to_ver}'

    repo = Repo(".")
    commits = list(repo.iter_commits(git_rev_list, max_count=500, min_parents=2))

    pr_id_set = set(map(get_pull_request_id, commits)) - {None}

    g = Github(github_token)
    gh_repo = g.get_repo("telstra/open-kilda")
    pr_list = [gh_repo.get_pull(n) for n in pr_id_set]

    features = []
    bugs = []
    improvements = []
    issues = []
    components = set()

    for v in pr_list:
        labels = [l.name for l in v.get_labels()]
        components |= {l[2:].lower() for l in labels if l.startswith("C/")}
        pr = {
            'id': v.number,
            'title': v.title,
            'labels': sorted([l for l in labels if lable_filter(l)]),
            'issues': sorted(get_issues_from_pr(v)),
            'topics': sorted(generate_topic_list(labels))
        }

        if 'feature' in labels:
            features.append(pr)
        elif 'bug' in labels:
            bugs.append(pr)
        elif 'improvement' in labels:
            improvements.append(pr)
        else:
            issues.append(pr)

    env = Environment(
        loader=FileSystemLoader('./tools/changelog-generator')
    )

    template = env.get_template("template.jinja2")
    print(template.render(features=features,
                          improvements=improvements,
                          issues=issues,
                          bugs=bugs,
                          components=components,
                          from_ver=from_ver,
                          to_ver=to_ver,
                          date=datetime.date.today().strftime("%d/%m/%Y")))


if __name__ == '__main__':
    main(auto_envvar_prefix='CHANGELOG_GENERATOR')
