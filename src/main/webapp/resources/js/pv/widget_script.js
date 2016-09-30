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

    // add inspector menu
    var menu_div = document.createElement('div');
    menu_div.setAttribute("id","inspector");
    menu_div.setAttribute("scoped","scoped");

    var heading = document.createElement('h2');
    var heading_text = document.createTextNode("Choose Style:");
    heading.appendChild(heading_text);
    menu_div.appendChild(heading);

    var menu_list = document.createElement('ul');

    var item_1 = document.createElement('li');
    item_1.setAttribute("id","preset");
    var text_1 = document.createTextNode("Preset");
    item_1.appendChild(text_1);
    menu_list.appendChild(item_1);

    var item_2 = document.createElement('li');
    item_2.setAttribute("id","cartoon");
    var text_2 = document.createTextNode("Cartoon");
    item_2.appendChild(text_2);
    menu_list.appendChild(item_2);

    var item_3 = document.createElement('li');
    item_3.setAttribute("id","tube");
    var text_3 = document.createTextNode("Tube");
    item_3.appendChild(text_3);
    menu_list.appendChild(item_3);

    var item_4 = document.createElement('li');
    item_4.setAttribute("id","lines");
    var text_4 = document.createTextNode("Lines");
    item_4.appendChild(text_4);
    menu_list.appendChild(item_4);

    var item_5 = document.createElement('li');
    item_5.setAttribute("id","line-trace");
    var text_5 = document.createTextNode("Line Trace");
    item_5.appendChild(text_5);
    menu_list.appendChild(item_5);

    var item_6 = document.createElement('li');
    item_6.setAttribute("id","sline");
    var text_6 = document.createTextNode("Smooth Line Trace");
    item_6.appendChild(text_6);
    menu_list.appendChild(item_6);

    var item_7 = document.createElement('li');
    item_7.setAttribute("id","trace");
    var text_7 = document.createTextNode("Trace");
    item_7.appendChild(text_7);
    menu_list.appendChild(item_7);

    menu_div.appendChild(menu_list);

    widget_div.parentNode.insertBefore(menu_div, widget_div.nextSibling);

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
            "https://dv.sbgrid.org/javax.faces.resource/js/pv/bio-pv-gz-support.min.js.xhtml?version=4.4");
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
            width: 300,
            height: 300,
            antialias: true,
            quality : 'medium'
        };
        proteinViewer = pv.Viewer(document.getElementById('viewer'), options);
        test();
    }

})();