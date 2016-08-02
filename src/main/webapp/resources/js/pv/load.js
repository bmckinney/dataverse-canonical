
(function() {

        // if(document.getElementById('data-pdb') != null) {
        //     var pdbid = window.frameElement.getAttribute('data-pdb');
        // } 
        // else {
        //     var pdbid = "5ddk";
        // }
        
        // usually just use this
        //var pdbid = window.frameElement.getAttribute('data-pdb');
        
        // var folder = pdbid.charAt(1).concat(pdbid.charAt(2));
        // var url = 'pdb/' + folder + '/pdb' + pdbid + '.ent.gz';
        
        // var element = document.getElementById('gl');
        // var viewer = pv.Viewer(element, 
        //                     { quality : 'high', width: 'auto', height : 'auto',
        //                       antialias : true, outline : true});
        // var options = {
        //         width: 50,
        //         height: 50,
        //         antialias: true,
        //         quality : 'medium'
        // };
        // proteinViewer = pv.Viewer(document.getElementById('viewer'), options);
        console.log("script entered");
         
        // function load(pdbid) {
        //     console.log("load funct entered");
        //     //document.getElementById('status').innerHTML ='loading ' + pdbid;
        //     pv.io.fetchPdb(url, function(structure) {
        //       window.structure = structure;
        //       preset();
        //       proteinViewer.centerOn(structure);
        //     });
        //     document.getElementById('status').innerHTML = '';
        // }
      
        function loadProtein() {
            console.log("loadProtein");
            load(pdbid);
        }
        function lines() {
            proteinViewer.clear();
            proteinViewer.lines('structure', structure);
        }
        function cartoon() {
            proteinViewer.clear();
            proteinViewer.cartoon('structure', structure, { color: color.ssSuccession() });
        }
        function lineTrace() {
            proteinViewer.clear();
            proteinViewer.lineTrace('structure', structure);
        }
        function sline() {
            console.log("IN SLINE");
            proteinViewer.clear();
            proteinViewer.sline('structure', structure);
        }
        function tube() {
            proteinViewer.clear();
            proteinViewer.tube('structure', structure);
        }
        function trace() {
            proteinViewer.clear();
            proteinViewer.trace('structure', structure);
        }
        // function preset() {
        //     proteinViewer.clear();
        //     var ligand = structure.select({rnames : ['RVP', 'SAH']});
        //     proteinViewer.ballsAndSticks('ligand', ligand);
        //     proteinViewer.cartoon('protein', structure);
        //     proteinViewer.autoZoom();
        // }
      
        document.getElementById('cartoon').onclick = cartoon;
        document.getElementById('line-trace').onclick = lineTrace;
        document.getElementById('preset').onclick = preset;
        document.getElementById('lines').onclick = lines;
        document.getElementById('trace').onclick = trace;
        document.getElementById('sline').onclick = sline;
        document.getElementById('tube').onclick = tube; 
/*            window.onresize = function(event) {
            pv.fitParent();
          } */
        document.addEventListener('DOMContentLoaded', loadProtein);



})();

