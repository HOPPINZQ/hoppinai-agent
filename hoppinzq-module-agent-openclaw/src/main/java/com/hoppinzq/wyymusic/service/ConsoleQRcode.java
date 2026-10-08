package com.hoppinzq.wyymusic.service;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * @Author 薄荷你玩
 * @Date 2023/04/16
 * @Website www.bhshare.cn
 */
public class ConsoleQRcode {

    public static void main(String[] args) throws IOException, InterruptedException {
        Image image = ImageIO.read(new File("D:\\csdn\\qrcode.png")); // 读取图像文件
        JLabel label = new JLabel(new ImageIcon(image)); // 将图像添加到标签中
        JFrame frame = new JFrame(); // 创建窗口
        frame.add(label); // 将标签添加到窗口中
        frame.setTitle("请使用网易云APP扫码"); // 设置窗口标题
        frame.setLocationRelativeTo(null); // 将窗口居中显示
        frame.setIconImage(ImageIO.read(new URL("https://hoppinzq.com/api/static/picture/chatGPT.png")));
        frame.setSize(900, 600); // 设置窗口大小为图像大小
        frame.pack(); // 根据标签大小调整窗口大小
        frame.setVisible(true); // 显示窗口
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // 设置窗口关闭操作

        Thread.sleep(10000);
        frame.dispose();
    }
}
