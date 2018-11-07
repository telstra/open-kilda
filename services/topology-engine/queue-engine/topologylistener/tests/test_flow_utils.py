import unittest
from topologylistener import flow_utils

COOKIE = 0x4000000000000001
METER = 12

class TestFlowUtils(unittest.TestCase):

    def test_validate_switch_rules_empty(self):
        self.check_validate_switch()

    def test_validate_switch_rules_missed_rules(self):
        self.add_flow_segment(COOKIE)
        self.check_validate_switch(missing_rules={COOKIE})

    def test_validate_switch_rules_excess_rules(self):
        self.add_switch_rule(COOKIE)
        self.check_validate_switch(excess_rules={COOKIE})

    def test_validate_switch_rules_proper_rules(self):
        self.add_switch_rule(COOKIE)
        self.add_flow_segment(COOKIE)
        self.check_validate_switch(proper_rules={COOKIE})

    def test_validate_switch_rules_missed_meters(self):
        self.add_flow(COOKIE, METER)
        self.check_validate_switch(missing_rules={COOKIE}, missing_meters={COOKIE})

    def test_validate_switch_rules_misconfigured_meters(self):
        self.add_flow(COOKIE, METER)
        self.add_switch_rule(COOKIE)
        self.check_validate_switch(proper_rules={COOKIE}, misconfigured_meters={COOKIE})

    def test_validate_switch_rules_excess_meters(self):
        self.add_flow_segment(COOKIE)
        self.add_switch_rule(COOKIE, METER)
        self.check_validate_switch(proper_rules={COOKIE}, excess_meters={COOKIE})

    def test_validate_switch_rules_excess_rules_and_meters(self):
        self.add_switch_rule(COOKIE, METER)
        self.check_validate_switch(excess_rules={COOKIE}, excess_meters={COOKIE})

    def test_validate_switch_rules_proper_meters(self):
        self.add_flow(COOKIE, METER)
        self.add_switch_rule(COOKIE, METER)
        self.check_validate_switch(proper_rules={COOKIE}, proper_meters={COOKIE})

    @classmethod
    def setUpClass(cls):
        cls.old_get_flow_segments_by_dst_switch = flow_utils.get_flow_segments_by_dst_switch
        cls.old_get_flows_by_src_switch = flow_utils.get_flows_by_src_switch

    @classmethod
    def tearDownClass(cls):
        flow_utils.get_flow_segments_by_dst_switch = cls.old_get_flow_segments_by_dst_switch
        flow_utils.get_flows_by_src_switch = cls.old_get_flows_by_src_switch

    def setUp(self):
        flow_utils.get_flow_segments_by_dst_switch = self.get_flow_segments_by_dst_switch
        flow_utils.get_flows_by_src_switch = self.get_flows_by_src_switch
        self.flow_segments = []
        self.flows = []
        self.switch_rules = []

    def get_flow_segments_by_dst_switch(self, switch_id):
        return self.flow_segments

    def get_flows_by_src_switch(self, switch_id):
        return self.flows

    def add_flow_segment(self, cookie):
        self.flow_segments.append({'cookie': cookie, 'parent_cookie': cookie})

    def add_flow(self, cookie, meter_id):
        self.flows.append({'cookie': cookie, 'meter_id': meter_id})

    def add_switch_rule(self, cookie, meter_id=None):
        self.switch_rules.append({
            'cookie': cookie,
            'instructions': {'instruction_goto_meter': meter_id}
        })

    def check_validate_switch(self, missing_rules=None, excess_rules=None, proper_rules=None, missing_meters=None,
                              misconfigured_meters=None, excess_meters=None, proper_meters=None):
        expected = {
            'rules': {
                'missing': missing_rules or set(),
                'misconfigured': set(),
                'excess': excess_rules or set(),
                'proper': proper_rules or set()
            },
            'meters': {
                'missing': missing_meters or set(),
                'misconfigured': misconfigured_meters or set(),
                'excess': excess_meters or set(),
                'proper': proper_meters or set()
            }
        }
        diff = flow_utils.validate_switch_rules("00:08", self.switch_rules)
        self.assertEqual(expected, diff)
