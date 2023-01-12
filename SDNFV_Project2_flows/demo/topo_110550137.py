from mininet.topo import Topo

class Project1_Topo_110550137(Topo):
    def __init__(self):
        Topo.__init__(self)

        # Add hosts
        h1 = self.addHost('h1', ip='192.168.130.1/27', mac='00:00:00:00:00:01')
        h2 = self.addHost('h2', ip='192.168.130.2/27', mac='00:00:00:00:00:02')
        h3 = self.addHost('h3', ip='192.168.130.3/27', mac='00:00:00:00:00:03')

        # Add switches
        s1 = self.addSwitch('s1')
        
        # Add links
        self.addLink(h1, s1)
        self.addLink(h2, s1)
        self.addLink(h3, s1)
        

topos = { 'topo_demo_110550137': Project1_Topo_110550137 }
