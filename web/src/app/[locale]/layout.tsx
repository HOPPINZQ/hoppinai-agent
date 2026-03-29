import type { Metadata } from "next";
import { I18nProvider } from "@/lib/i18n";
import { Header } from "@/components/layout/header";
import zh from "@/i18n/messages/zh.json";
import "../globals.css";

const locales = ["zh"];
const metaMessages: Record<string, typeof zh> = {zh};

export function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const messages = metaMessages[locale] || metaMessages.zh;
  return {
    title: messages.meta?.title || "手撕Claude Code",
    description: messages.meta?.description || "从0到1开发一个AI编程智能体",
  };
}

export default async function RootLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;

  return (
    <html lang={locale} className="dark" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: `
          (function() {
            document.documentElement.classList.add('dark');
            localStorage.setItem('theme', 'dark');
          })();
        `}} />
      </head>
      <body className="min-h-screen bg-gradient-to-br from-[#0d0b1a] via-[#110e24] to-[#080612] text-[var(--color-text)] antialiased">
        <I18nProvider locale={locale}>
          <Header />
          <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
            {children}
          </main>
        </I18nProvider>
      </body>
    </html>
  );
}
