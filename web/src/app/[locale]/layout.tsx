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
    keywords: ["AI Agent", "Java", "Claude Code", "智能体", "AI编程", "LLM", "MCP协议", "ReAct", "ZQAgent", "Anthropic API"],
    authors: [{ name: "hoppinzq" }],
    openGraph: {
      title: messages.meta?.title || "手撕Claude Code",
      description: messages.meta?.description || "从0到1开发一个AI编程智能体",
      type: "website",
      locale: locale === "zh" ? "zh_CN" : "en_US",
      siteName: "手撕Claude Code",
    },
    twitter: {
      card: "summary_large_image",
      title: messages.meta?.title || "手撕Claude Code",
      description: messages.meta?.description || "从0到1开发一个AI编程智能体",
    },
    icons: {
      icon: "https://cdn.hoppinzq.com/static/images/favicon.ico",
      shortcut: "https://cdn.hoppinzq.com/static/images/favicon.ico",
    },
    robots: {
      index: true,
      follow: true,
    },
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
        <script dangerouslySetInnerHTML={{ __html: `
          (function(c,l,a,r,i,t,y){
              c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};
              t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;
              y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y);
          })(window, document, "clarity", "script", "w3nbs5o5r7");
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
