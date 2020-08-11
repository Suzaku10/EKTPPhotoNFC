import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:ektp_reader/ektp_reader.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _base64String;
  String _path;

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      await EktpReader.getFilePathEKTPPhoto((path) {
        setState(() {
          if (!mounted) return;
          _path = path;
        });
      });

      await EktpReader.getEktpPhoto((base64) => setState(() {
            print(base64);
            if (!mounted) return;
            _base64String = base64;
          }));
    } on PlatformException {
      print("error");
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
          appBar: AppBar(
            title: const Text('Plugin example app'),
          ),
          body: Column(
            children: <Widget>[
              _showEKTPPhoto()
              //  Image.memory(base64Decode(_base64String2)),
              /* _base64String != null
                  ? Image.memory(
                      base64Decode(_base64String),
                      scale: 0.5,
                      filterQuality: FilterQuality.high,
                    )
                  : Text("Waiting For Photo"),
              _base64String != null
                  ? Image.memory(
                      base64Decode(_base64String),
                      height: 200,
                      width: 150,
                      filterQuality: FilterQuality.high,
                    )
                  : Text("Waiting For Photo"),
              _base64String != null
                  ? Image.memory(
                      base64Decode(_base64String),
                      filterQuality: FilterQuality.high,
                    )
                  : Text("Waiting For Photo")*/
            ],
          )),
    );
  }

  Widget _showEKTPPhoto() {
    if (_base64String == null) {
      if (_path != null) {
        return Image.file(File(_path),
            scale: 0.5, filterQuality: FilterQuality.high);
      } else {
        return Text("Waiting For Photo");
      }
    } else {
      return Image.memory(base64Decode(_base64String),
          scale: 0.5, filterQuality: FilterQuality.high);
    }
  }
}
