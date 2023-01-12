from mininet.topo import Topo

class MyTopo( Topo ):
    def __init__( self ):
        Topo.__init__( self )

        # Add hosts
        h1 = self.addHost( 'h1' )
        h2 = self.addHost( 'h2' )

        # Add switches
        s1 = self.addSwitch( 's1' )
        
        # Add links
        self.addLink( h1, s1 )
        self.addLink( h2, s1 )


topos = { 'mytopo': MyTopo }