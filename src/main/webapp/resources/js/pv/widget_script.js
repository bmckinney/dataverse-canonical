(function() {

    // css
    var css_bootstrap = document.createElement('link');
    css_bootstrap.setAttribute("type","text/css");
    css_bootstrap.setAttribute("rel","stylesheet");
    css_bootstrap.setAttribute("href","https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css");
    (document.getElementsByTagName("head")[0] || document.documentElement).appendChild(css_bootstrap);

    var css_font_awesome = document.createElement('link');
    css_font_awesome.setAttribute("type","text/css");
    css_font_awesome.setAttribute("rel","stylesheet");
    css_font_awesome.setAttribute("href","https://maxcdn.bootstrapcdn.com/font-awesome/4.3.0/css/font-awesome.min.css");
    (document.getElementsByTagName("head")[0] || document.documentElement).appendChild(css_font_awesome);

    // insert widget div after this script
    var widget_div = document.createElement('div');
    widget_div.setAttribute("id","viewer");
    widget_div.setAttribute("scoped","scoped");
    var current_script = document.getElementById("pv-widget");
    current_script.parentNode.insertBefore(widget_div, current_script.nextSibling);

    var proteinViewer;
    if (window.proteinViewer === undefined) {
        loadZlib();
    } else {
        proteinViewer = window.proteinViewer;
        main();
    }

    function loadPv() {
        var script_tag = document.createElement('script');
        script_tag.setAttribute("type","text/javascript");
        script_tag.setAttribute("src",
            "https://dv.sbgrid.org/javax.faces.resource/js/pv/bio-pv.min.js.xhtml?version=4.4");
        if (script_tag.readyState) {
            script_tag.onreadystatechange = function () { // For old versions of IE
                if (this.readyState == 'complete' || this.readyState == 'loaded') {
                    main();
                }
            };
        } else {
            script_tag.onload = main;
        }
        current_script.parentNode.insertBefore(script_tag, current_script.nextSibling);
    }

    function loadZlib() {
        var script_zlib = document.createElement('script');
        script_zlib.setAttribute("type","text/javascript");
        script_zlib.setAttribute("src",
            "https://dv.sbgrid.org/javax.faces.resource/js/pv/gunzip.min.js.xhtml?version=4.4");
        if (script_zlib.readyState) {
            script_zlib.onreadystatechange = function () { // For old versions of IE
                if (this.readyState == 'complete' || this.readyState == 'loaded') {
                    loadPv();
                }
            };
        } else {
            script_zlib.onload = loadPv();
        }
        current_script.parentNode.insertBefore(script_zlib, current_script.nextSibling);
    }

    function preset() {
        proteinViewer.clear();
        var ligand = structure.select({rnames : ['RVP', 'SAH']});
        proteinViewer.ballsAndSticks('ligand', ligand);
        proteinViewer.cartoon('protein', structure);
    }

    function load(pdbid) {
        console.log("inside load...");
        pdbid = pdbid.toLowerCase();
        var folder = pdbid.charAt(1).concat(pdbid.charAt(2));
        var url = 'pdb/' + folder + '/pdb' + pdbid + '.ent.gz';
        pv.io.fetchPdb(url, function(structure) {
            window.structure = structure;
            preset();
            proteinViewer.centerOn(structure);
        });
    }

    function test() {
        load('1r6a');
    }

    function main() {
        var options = {
            width: 600,
            height: 600,
            antialias: true,
            quality : 'medium'
        };
        proteinViewer = pv.Viewer(document.getElementById('viewer'), options);
        test();
    }

})();